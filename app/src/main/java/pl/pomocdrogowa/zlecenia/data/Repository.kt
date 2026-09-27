package pl.pomocdrogowa.zlecenia.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/** Cały dostęp do Firebase w jednym miejscu. */
object Repository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()

    private val users get() = db.collection("users")
    private val orders get() = db.collection("orders")
    private val setupDoc get() = db.document("config/setup")
    private val countersDoc get() = db.document("config/counters")

    // ---------- Logowanie ----------

    fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                users.document(uid).update("fcmTokens", FieldValue.arrayRemove(token)).await()
            }
        }
        auth.signOut()
    }

    /** Czy w firmie założono już pierwsze konto dyspozytora. */
    suspend fun isSetupDone(): Boolean = setupDoc.get().await().exists()

    /** Pierwsze uruchomienie: zakłada konto głównego dyspozytora. */
    suspend fun registerFirstDispatcher(name: String, email: String, password: String, phone: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user!!.uid
        db.batch()
            .set(users.document(uid), newUserData(name, email, phone, "", Role.DISPATCHER))
            .set(setupDoc, mapOf("createdAt" to FieldValue.serverTimestamp(), "by" to uid))
            .commit()
            .await()
    }

    // ---------- Profile ----------

    fun profile(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.toUser())
        }
        awaitClose { reg.remove() }
    }

    fun allUsers(): Flow<List<UserProfile>> = callbackFlow {
        val reg = users.addSnapshotListener { snap, _ ->
            if (snap != null) trySend(snap.documents.map { it.toUser() }.sortedBy { it.name.lowercase() })
        }
        awaitClose { reg.remove() }
    }

    /**
     * Dyspozytor zakłada konto kierowcy (lub kolejnego dyspozytora).
     * Używamy osobnej instancji FirebaseApp, żeby dyspozytor nie został wylogowany.
     */
    suspend fun createUser(
        context: Context,
        name: String,
        email: String,
        password: String,
        phone: String,
        vehicle: String,
        role: Role,
    ) {
        val secondary = FirebaseApp.getApps(context).firstOrNull { it.name == "user-creator" }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "user-creator")
        val secondaryAuth = FirebaseAuth.getInstance(secondary)
        val result = secondaryAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user!!.uid
        secondaryAuth.signOut()
        users.document(uid).set(newUserData(name, email, phone, vehicle, role)).await()
    }

    suspend fun updateUser(uid: String, changes: Map<String, Any?>) {
        users.document(uid).update(changes).await()
    }

    private fun newUserData(name: String, email: String, phone: String, vehicle: String, role: Role) = mapOf(
        "name" to name.trim(),
        "email" to email.trim().lowercase(),
        "phone" to phone.trim(),
        "vehicle" to vehicle.trim(),
        "role" to role.key,
        "active" to true,
        "fcmTokens" to emptyList<String>(),
        "createdAt" to FieldValue.serverTimestamp(),
    )

    suspend fun registerPushToken(token: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val t = token ?: FirebaseMessaging.getInstance().token.await()
        users.document(uid).update("fcmTokens", FieldValue.arrayUnion(t)).await()
    }

    // ---------- Zlecenia ----------

    fun ordersFor(user: UserProfile): Flow<List<Order>> = callbackFlow {
        val query: Query = if (user.isDispatcher) {
            orders.orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
        } else {
            // Reguły bezpieczeństwa pozwalają kierowcy czytać tylko jego zlecenia.
            orders.whereEqualTo("driverId", user.uid)
        }
        val reg = query.addSnapshotListener { snap, _ ->
            if (snap != null) {
                trySend(snap.documents.map { it.toOrder() }.sortedByDescending { it.createdAt })
            }
        }
        awaitClose { reg.remove() }
    }

    fun order(id: String): Flow<Order?> = callbackFlow {
        val reg = orders.document(id).addSnapshotListener { snap, _ ->
            trySend(snap?.takeIf { it.exists() }?.toOrder())
        }
        awaitClose { reg.remove() }
    }

    /** Tworzy zlecenie z kolejnym numerem. Zwraca id dokumentu. */
    suspend fun createOrder(draft: OrderDraft, author: UserProfile): String {
        val ref = orders.document()
        db.runTransaction { tr ->
            val counter = tr.get(countersDoc)
            val number = (counter.getLong("orders") ?: 0L) + 1
            tr.set(countersDoc, mapOf("orders" to number), SetOptions.merge())
            tr.set(
                ref,
                draft.toMap() + mapOf(
                    "number" to number,
                    "status" to OrderStatus.NEW.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdBy" to author.uid,
                    "lastMessage" to "Nowe zlecenie",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.await()
        return ref.id
    }

    suspend fun updateOrder(id: String, draft: OrderDraft) {
        orders.document(id).update(draft.toMap() + ("updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun deleteOrder(id: String) {
        orders.document(id).delete().await()
    }

    suspend fun changeStatus(orderId: String, status: OrderStatus, by: UserProfile) {
        val text = "Status: ${status.label}"
        val batch = db.batch()
        batch.update(
            orders.document(orderId),
            mapOf(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastMessage" to text,
                "lastMessageAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            orders.document(orderId).collection("messages").document(),
            messageData(MessageType.SYSTEM, by, text = text),
        )
        batch.commit().await()
    }

    private fun OrderDraft.toMap(): Map<String, Any?> = mapOf(
        "driverId" to driverId,
        "driverName" to driverName,
        "clientName" to clientName.trim(),
        "clientPhone" to clientPhone.trim(),
        "vehicle" to vehicle.trim(),
        "plate" to plate.trim().uppercase(),
        "pickupAddress" to pickupAddress.trim(),
        "destinationAddress" to destinationAddress.trim(),
        "description" to description.trim(),
    )

    // ---------- Wiadomości: komentarze, zdjęcia, głosówki, dane ----------

    fun messages(orderId: String): Flow<List<Message>> = callbackFlow {
        val reg = orders.document(orderId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) trySend(snap.documents.map { it.toMessage() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(orderId: String, sender: UserProfile, text: String) {
        postMessage(orderId, messageData(MessageType.TEXT, sender, text = text.trim()), text.trim())
    }

    suspend fun sendData(orderId: String, sender: UserProfile, fields: List<DataField>) {
        val data = messageData(MessageType.DATA, sender, fields = fields)
        postMessage(orderId, data, "Dane dodatkowe: " + fields.joinToString { it.label })
    }

    suspend fun sendPhoto(orderId: String, sender: UserProfile, jpeg: File) {
        val url = upload(orderId, "photos/${UUID.randomUUID()}.jpg", Uri.fromFile(jpeg), "image/jpeg")
        postMessage(orderId, messageData(MessageType.PHOTO, sender, mediaUrl = url), "📷 Zdjęcie")
    }

    suspend fun sendAudio(orderId: String, sender: UserProfile, file: File, durationMs: Long) {
        val url = upload(orderId, "audio/${UUID.randomUUID()}.m4a", Uri.fromFile(file), "audio/mp4")
        postMessage(
            orderId,
            messageData(MessageType.AUDIO, sender, mediaUrl = url, durationMs = durationMs),
            "🎤 Wiadomość głosowa",
        )
    }

    private suspend fun upload(orderId: String, path: String, uri: Uri, mime: String): String {
        val ref = storage.reference.child("orders/$orderId/$path")
        ref.putFile(uri, StorageMetadata.Builder().setContentType(mime).build()).await()
        return ref.downloadUrl.await().toString()
    }

    private suspend fun postMessage(orderId: String, data: Map<String, Any?>, preview: String) {
        val orderRef = orders.document(orderId)
        db.batch()
            .set(orderRef.collection("messages").document(), data)
            .update(
                orderRef,
                mapOf(
                    "lastMessage" to "${data["senderName"]}: $preview".take(200),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                ),
            )
            .commit()
            .await()
    }

    private fun messageData(
        type: MessageType,
        sender: UserProfile,
        text: String = "",
        mediaUrl: String? = null,
        durationMs: Long = 0,
        fields: List<DataField> = emptyList(),
    ) = mapOf(
        "type" to type.name,
        "text" to text,
        "mediaUrl" to mediaUrl,
        "durationMs" to durationMs,
        "fields" to fields.map { mapOf("label" to it.label, "value" to it.value) },
        "senderId" to sender.uid,
        "senderName" to sender.name,
        "senderRole" to sender.role.key,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    // ---------- Mapowanie dokumentów ----------

    private fun DocumentSnapshot.str(field: String) = getString(field).orEmpty()

    private fun DocumentSnapshot.date(field: String) =
        getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()

    private fun DocumentSnapshot.toUser() = UserProfile(
        uid = id,
        name = str("name"),
        email = str("email"),
        phone = str("phone"),
        vehicle = str("vehicle"),
        role = Role.from(getString("role")),
        active = getBoolean("active") ?: true,
    )

    private fun DocumentSnapshot.toOrder() = Order(
        id = id,
        number = getLong("number") ?: 0L,
        status = OrderStatus.from(getString("status")),
        driverId = getString("driverId"),
        driverName = str("driverName"),
        clientName = str("clientName"),
        clientPhone = str("clientPhone"),
        vehicle = str("vehicle"),
        plate = str("plate"),
        pickupAddress = str("pickupAddress"),
        destinationAddress = str("destinationAddress"),
        description = str("description"),
        createdAt = date("createdAt"),
        lastMessage = str("lastMessage"),
        lastMessageAt = date("lastMessageAt"),
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toMessage(): Message {
        val rawFields = get("fields") as? List<Map<String, Any?>> ?: emptyList()
        return Message(
            id = id,
            type = runCatching { MessageType.valueOf(str("type")) }.getOrDefault(MessageType.TEXT),
            text = str("text"),
            mediaUrl = getString("mediaUrl"),
            durationMs = getLong("durationMs") ?: 0L,
            fields = rawFields.map { DataField(it["label"]?.toString().orEmpty(), it["value"]?.toString().orEmpty()) },
            senderId = str("senderId"),
            senderName = str("senderName"),
            senderRole = Role.from(getString("senderRole")),
            createdAt = date("createdAt"),
        )
    }
}
