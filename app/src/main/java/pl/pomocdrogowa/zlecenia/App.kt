package pl.pomocdrogowa.zlecenia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, "Nowe zlecenia", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Powiadomienia o przydzielonych zleceniach"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Wiadomości", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Komentarze, zdjęcia i głosówki w zleceniach"
            },
        )
    }

    companion object {
        const val CHANNEL_ORDERS = "orders"
        const val CHANNEL_MESSAGES = "messages"
    }
}
