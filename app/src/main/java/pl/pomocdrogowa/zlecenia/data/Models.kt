package pl.pomocdrogowa.zlecenia.data

import androidx.compose.ui.graphics.Color
import java.util.Date

enum class Role(val label: String) {
    DISPATCHER("Dyspozytor"),
    DRIVER("Kierowca");

    val key: String get() = name.lowercase()

    companion object {
        fun from(value: String?): Role = entries.firstOrNull { it.key == value } ?: DRIVER
    }
}

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String,
    val vehicle: String,
    val role: Role,
    val active: Boolean,
) {
    val isDispatcher: Boolean get() = role == Role.DISPATCHER
}

enum class OrderStatus(val label: String, val color: Color) {
    NEW("Nowe", Color(0xFF1E88E5)),
    ACCEPTED("Przyjęte", Color(0xFF5E35B1)),
    ON_THE_WAY("W drodze", Color(0xFFF57C00)),
    ON_SITE("Na miejscu", Color(0xFFE53935)),
    TOWING("Holowanie", Color(0xFF8D6E63)),
    DONE("Zakończone", Color(0xFF43A047)),
    CANCELLED("Anulowane", Color(0xFF757575));

    val isFinished: Boolean get() = this == DONE || this == CANCELLED

    /** Kolejny krok, który kierowca może zgłosić jednym przyciskiem. */
    val next: OrderStatus?
        get() = when (this) {
            NEW -> ACCEPTED
            ACCEPTED -> ON_THE_WAY
            ON_THE_WAY -> ON_SITE
            ON_SITE -> TOWING
            TOWING -> DONE
            DONE, CANCELLED -> null
        }

    companion object {
        fun from(value: String?): OrderStatus = entries.firstOrNull { it.name == value } ?: NEW
    }
}

data class Order(
    val id: String,
    val number: Long,
    val status: OrderStatus,
    val driverId: String?,
    val driverName: String,
    val clientName: String,
    val clientPhone: String,
    val vehicle: String,
    val plate: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val description: String,
    val createdAt: Date?,
    val lastMessage: String,
    val lastMessageAt: Date?,
)

/** Pola formularza zlecenia (tworzenie i edycja). */
data class OrderDraft(
    val driverId: String? = null,
    val driverName: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val vehicle: String = "",
    val plate: String = "",
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val description: String = "",
)

fun Order.toDraft() = OrderDraft(
    driverId = driverId,
    driverName = driverName,
    clientName = clientName,
    clientPhone = clientPhone,
    vehicle = vehicle,
    plate = plate,
    pickupAddress = pickupAddress,
    destinationAddress = destinationAddress,
    description = description,
)

enum class MessageType { TEXT, PHOTO, AUDIO, DATA, SYSTEM }

data class DataField(val label: String, val value: String)

data class Message(
    val id: String,
    val type: MessageType,
    val text: String,
    val mediaUrl: String?,
    val durationMs: Long,
    val fields: List<DataField>,
    val senderId: String,
    val senderName: String,
    val senderRole: Role,
    val createdAt: Date?,
)
