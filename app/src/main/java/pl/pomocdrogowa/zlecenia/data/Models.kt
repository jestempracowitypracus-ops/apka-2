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

/** Dostępność kierowcy - ustawia ją kierowca, widzi dyspozytor. */
enum class Availability(val label: String, val color: Color) {
    FREE("Wolny", Color(0xFF43A047)),
    BUSY("Zajęty", Color(0xFFF57C00)),
    OFF("Po pracy", Color(0xFF9E9E9E));

    val key: String get() = name.lowercase()

    /** Czy kierowca jest w pracy - wtedy udostępnia lokalizację. */
    val onDuty: Boolean get() = this != OFF

    companion object {
        fun from(value: String?): Availability = entries.firstOrNull { it.key == value } ?: OFF
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
    val availability: Availability = Availability.OFF,
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
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val protocolUrl: String? = null,
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
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
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
    pickupLat = pickupLat,
    pickupLng = pickupLng,
)

enum class MessageType { TEXT, PHOTO, AUDIO, DATA, PROTOCOL, SYSTEM }

/** Ostatnia znana pozycja kierowcy (kolekcja locations). */
data class DriverLocation(
    val uid: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val updatedAt: Date?,
) {
    /** Pozycja starsza niż 15 minut - kierowca mógł stracić zasięg. */
    val isStale: Boolean
        get() = updatedAt == null || System.currentTimeMillis() - updatedAt.time > 15 * 60_000
}

/** Odległość w km między dwoma punktami (wzór haversine). */
fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

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
