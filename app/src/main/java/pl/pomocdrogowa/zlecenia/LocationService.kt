package pl.pomocdrogowa.zlecenia

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.pomocdrogowa.zlecenia.data.Repository

/**
 * Działa, gdy kierowca jest w pracy (Wolny / Zajęty): co ~30 s (lub po przejechaniu 50 m)
 * zapisuje jego pozycję, żeby dyspozytor widział go na mapie. Stałe powiadomienie
 * informuje kierowcę, że lokalizacja jest udostępniana.
 */
class LocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var driverName = ""
    private var started = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            scope.launch {
                runCatching {
                    Repository.saveMyLocation(driverName, location.latitude, location.longitude, location.accuracy)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_NAME)?.let { driverName = it }
        if (!hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, App.CHANNEL_LOCATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jesteś w pracy")
            .setContentText("Dyspozytor widzi Twoją lokalizację. Ustaw „Po pracy”, aby wyłączyć.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
                .setMinUpdateDistanceMeters(50f)
                .setMinUpdateIntervalMillis(15_000L)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_NAME = "name"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context, driverName: String) {
            if (!hasLocationPermission(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, LocationService::class.java).putExtra(EXTRA_NAME, driverName),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
