package pl.pomocdrogowa.zlecenia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.osmdroid.config.Configuration
import java.io.File

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
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LOCATION, "Udostępnianie lokalizacji", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stałe powiadomienie, gdy kierowca jest w pracy"
            },
        )

        // Mapa OpenStreetMap: identyfikacja aplikacji i pamięć podręczna kafelków w katalogu aplikacji.
        Configuration.getInstance().apply {
            load(this@App, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
    }

    companion object {
        const val CHANNEL_ORDERS = "orders"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_LOCATION = "location"
    }
}
