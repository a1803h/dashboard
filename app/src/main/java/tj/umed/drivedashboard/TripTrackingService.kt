package tj.umed.drivedashboard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

/**
 * Android location foreground service.
 * Starts while Activity is visible and keeps GPS + trip timer alive when the screen is off.
 * Notification provides a user-controlled stop action.
 */
class TripTrackingService : Service() {
    companion object {
        const val ACTION_START = "tj.umed.drivedashboard.START_TRACKING"
        const val ACTION_STOP = "tj.umed.drivedashboard.STOP_TRACKING"
        private const val CHANNEL = "trip_tracking"
        private const val NOTIFICATION_ID = 5213
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dashboard: DashboardController
    private var started = false
    private var lastNotificationAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!started) return
            dashboard.tick()
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotificationAt >= 30_000L) {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, createNotification())
                lastNotificationAt = now
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dashboard = TripRuntime.get(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Отслеживание поездки",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает активную запись поездки и позволяет её остановить"
                setShowBadge(false)
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: IllegalStateException) {
            stopSelf()
            return START_NOT_STICKY
        }
        dashboard.start()
        if (!started) {
            started = true
            dashboard.tick()
            lastNotificationAt = SystemClock.elapsedRealtime()
            handler.postDelayed(ticker, 1000L)
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val toDashboard = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TripTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (dashboard.speedTileMode) {
            SpeedTileMode.MANUAL_PAUSED -> "Поездка — ручная пауза"
            SpeedTileMode.MANUAL_RUNNING -> "Поездка — ручная запись"
            SpeedTileMode.AUTOMATIC -> if (dashboard.isRecording)
                "Поездка — запись" else "Поездка — ожидание движения"
        }
        val distance = String.format(Locale.getDefault(), "%.1f", dashboard.distanceKm)
        val minutes = dashboard.elapsedMs / 60_000L
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("Пройдено: ${distance} км · Прошло: ${minutes} мин")
            .setContentIntent(toDashboard)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Остановить",
                    stop
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        started = false
        handler.removeCallbacks(ticker)
        if (::dashboard.isInitialized) dashboard.stop()
        super.onDestroy()
    }
}
