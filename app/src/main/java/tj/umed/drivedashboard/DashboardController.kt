package tj.umed.drivedashboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

class TripActivityReceiver : BroadcastReceiver() {
    companion object {
        var callback: ((Int) -> Unit)? = null
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        ActivityTransitionResult.extractResult(intent)?.transitionEvents?.forEach { event ->
            if (event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                callback?.invoke(event.activityType)
            }
        }
    }
}

class DashboardController(val context: Context) {
    val store = DashboardStore(context)
    private val locations = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val detector = DrivingDetector()
    private var tracking = false
    private var recognitionRequested = false
    private var previousFix: Location? = null
    private var lastFixAt = 0L
    private var lastClockAt = SystemClock.elapsedRealtime()
    private var lastSavedAt = 0L
    private var lastBatteryReadAt = 0L
    private var accurateElapsedMs = store.travelMs()
    private var manualOverride: Boolean? = null

    var speedKmh by mutableStateOf(0.0); private set
    var gpsBars by mutableStateOf(0); private set
    var satellitesUsed by mutableStateOf(0); private set
    var gpsHasFix by mutableStateOf(false); private set
    var targetKm by mutableStateOf(store.targetKm()); private set
    var distanceKm by mutableStateOf(store.distanceKm()); private set
    var elapsedMs by mutableStateOf(store.travelMs()); private set
    var avgSpeedKmh by mutableStateOf(0.0); private set
    var arrivalMinutes by mutableStateOf<Int?>(null); private set
    var clock by mutableStateOf("--:--"); private set
    var batteryPercent by mutableStateOf(0); private set
    var pinnedApps by mutableStateOf(store.selectedPackages()); private set
    var brightness by mutableStateOf(store.brightness()); private set
    var locationGranted by mutableStateOf(false); private set
    var gpsEnabled by mutableStateOf(false); private set
    var recognitionGranted by mutableStateOf(false); private set
    var movement by mutableStateOf(Movement.WAITING); private set
    var isRecording by mutableStateOf(false); private set
    var manualControl by mutableStateOf(false); private set

    val remainingKm: Double get() = (targetKm - distanceKm).coerceAtLeast(0.0)

    private val listener = LocationListener { fix -> onFix(fix) }
    private val satellites = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var count = 0
            for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) count++
            satellitesUsed = count
            updateBars()
        }
        override fun onStopped() { satellitesUsed = 0; gpsBars = 0 }
    }

    private fun recognitionIntent(): PendingIntent {
        val intent = Intent(context, TripActivityReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 7001, intent, flags)
    }

    private fun startRecognition() {
        recognitionGranted = Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (!recognitionGranted || recognitionRequested) return
        val transitions = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT).map { type ->
            ActivityTransition.Builder()
                .setActivityType(type)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        TripActivityReceiver.callback = { type ->
            advanceClock(SystemClock.elapsedRealtime())
            when (type) {
                DetectedActivity.IN_VEHICLE -> detector.detectedVehicle()
                DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> detector.detectedWalking()
            }
            refreshRecording()
        }
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), recognitionIntent())
                .addOnSuccessListener { recognitionRequested = true }
                .addOnFailureListener { recognitionRequested = false }
        } catch (_: Exception) {
            recognitionRequested = false // GPS speed fallback remains available.
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (tracking) { startRecognition(); return }
        locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        gpsEnabled = runCatching { locations.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        lastClockAt = SystemClock.elapsedRealtime()
        previousFix = null
        gpsHasFix = false
        speedKmh = 0.0
        if (!locationGranted || !gpsEnabled) {
            gpsBars = 0
            return
        }
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500L, 1f, listener, Looper.getMainLooper())
            locations.registerGnssStatusCallback(satellites, handler)
            tracking = true
            startRecognition()
        } catch (_: Exception) {
            locationGranted = false
            tracking = false
        }
    }

    fun stop() {
        advanceClock(SystemClock.elapsedRealtime())
        if (tracking) {
            runCatching { locations.removeUpdates(listener) }
            runCatching { locations.unregisterGnssStatusCallback(satellites) }
        }
        tracking = false
        if (recognitionRequested) {
            runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(recognitionIntent()) }
        }
        recognitionRequested = false
        TripActivityReceiver.callback = null
        previousFix = null
        gpsHasFix = false
        gpsBars = 0
        speedKmh = 0.0
        lastClockAt = SystemClock.elapsedRealtime()
        persistTrip()
        saveBrightness()
    }

    private fun updateBars() {
        gpsBars = if (!gpsHasFix) 0 else when {
            satellitesUsed >= 12 -> 5
            satellitesUsed >= 9 -> 4
            satellitesUsed >= 6 -> 3
            satellitesUsed >= 4 -> 2
            else -> 1
        }
    }

    private fun advanceClock(now: Long) {
        if (isRecording && now > lastClockAt) {
            accurateElapsedMs += (now - lastClockAt).coerceAtMost(10000L)
        }
        lastClockAt = now
        // The visible "ПРОШЛО" tile changes by minutes, not every second.
        val visibleMinutes = (accurateElapsedMs / 60000L) * 60000L
        if (elapsedMs != visibleMinutes) elapsedMs = visibleMinutes
    }

    private fun refreshRecording() {
        movement = detector.movement
        manualControl = manualOverride != null
        // A target distance is optional: driving, speed and elapsed time work without it.
        isRecording = (targetKm <= 0.0 || remainingKm > 0.0) &&
            (manualOverride ?: (movement == Movement.DRIVING))
        updateCalculated()
    }

    private fun onFix(fix: Location) {
        if (!fix.hasAccuracy() || fix.accuracy > 35f) return
        val now = SystemClock.elapsedRealtime()
        advanceClock(now)
        lastFixAt = now
        gpsHasFix = true
        updateBars()
        val previous = previousFix
        var actualSpeed = if (fix.hasSpeed() && fix.speed.isFinite()) (fix.speed * 3.6).toDouble() else 0.0
        val dt = if (previous == null) 0.0 else
            (fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
        if (!fix.hasSpeed() && previous != null && dt in 0.5..10.0)
            actualSpeed = previous.distanceTo(fix).toDouble() / dt * 3.6
        if (actualSpeed !in 0.0..250.0) return
        // The speed tile always uses the latest GNSS speed, not an average.
        speedKmh = if (actualSpeed < 1.5) 0.0 else actualSpeed
        detector.observe(speedKmh, now)
        refreshRecording()

        if (previous != null && isRecording && dt in 0.5..10.0 &&
            previous.hasAccuracy() && previous.accuracy <= 25f && fix.accuracy <= 25f) {
            val meters = previous.distanceTo(fix).toDouble()
            val reasonable = meters >= 1.5 && meters < dt * 65.0 &&
                (speedKmh >= 2.0 || actualSpeed >= 2.0)
            if (reasonable) distanceKm += meters / 1000.0
        }
        previousFix = fix
        refreshRecording()
    }

    fun tick() {
        val now = SystemClock.elapsedRealtime()
        clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        // Battery broadcasts need reading only once a minute; clock text changes only on minute rollover.
        if (lastBatteryReadAt == 0L || now - lastBatteryReadAt >= 60_000L) {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) batteryPercent = (level * 100 / scale).coerceIn(0, 100)
            lastBatteryReadAt = now
        }
        advanceClock(now)
        if (gpsHasFix && now - lastFixAt > 5500) {
            gpsHasFix = false
            gpsBars = 0
            speedKmh = 0.0
            previousFix = null
        }
        refreshRecording()
        if (now - lastSavedAt > 5000) {
            persistTrip()
            lastSavedAt = now
        }
    }

    private fun updateCalculated() {
        avgSpeedKmh = if (accurateElapsedMs > 1000L && distanceKm > 0.0)
            distanceKm * 3_600_000.0 / accurateElapsedMs else 0.0
        arrivalMinutes = when {
            targetKm <= 0 -> null
            remainingKm <= 0 -> 0
            avgSpeedKmh >= 3.0 -> ceil(remainingKm / avgSpeedKmh * 60).toInt()
            else -> null
        }
    }

    fun setTarget(km: Double) {
        val validated = km.coerceIn(0.0, 99999.0)
        if (validated == targetKm) return
        // Setting the first destination during an already active untargeted drive
        // preserves driven kilometers and minutes; replacing a destination starts anew.
        val hadTarget = targetKm > 0.0
        if (hadTarget) {
            distanceKm = 0.0
            accurateElapsedMs = 0L
            elapsedMs = 0L
            manualOverride = null
            previousFix = null
        }
        targetKm = validated
        lastClockAt = SystemClock.elapsedRealtime()
        refreshRecording()
        persistTrip()
    }

    /** Reset tracked distance/time/ETA without clearing destination distance. */
    fun resetTripKeepingTarget() {
        val now = SystemClock.elapsedRealtime()
        distanceKm = 0.0
        accurateElapsedMs = 0L
        elapsedMs = 0L
        avgSpeedKmh = 0.0
        arrivalMinutes = null
        manualOverride = null
        previousFix = null // prevent a jump from a GPS fix before the reset
        lastClockAt = now
        refreshRecording()
        persistTrip()
    }

    /** Double tapping speed toggles forced PAUSE/PLAY; long press returns to AUTO. */
    fun toggleManualPause() {
        advanceClock(SystemClock.elapsedRealtime())
        manualOverride = !(manualOverride ?: (movement == Movement.DRIVING))
        refreshRecording()
    }

    fun useAutomaticMode() {
        advanceClock(SystemClock.elapsedRealtime())
        manualOverride = null
        refreshRecording()
    }

    fun setPinned(packages: List<String>) {
        pinnedApps = packages.distinct().take(8)
        store.saveSelectedPackages(pinnedApps)
    }

    fun updateBrightness(value: Float) { brightness = value.coerceIn(0.02f, 1f) }
    fun saveBrightness() { store.saveBrightness(brightness) }
    private fun persistTrip() { store.saveTrip(targetKm, distanceKm, accurateElapsedMs) }
}
