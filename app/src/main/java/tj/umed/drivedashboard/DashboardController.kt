package tj.umed.drivedashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DashboardController(val context: Context) {
    val store = DashboardStore(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val selector = GearSelector(1.5)
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var previousLocation: Location? = null
    private var tracking = false
    // A trip starts only on real GPS motion. Using wall time preserves running time
    // at traffic lights, while the app is backgrounded, and across process restarts.
    private var tripStartEpochMs: Long? = store.tripStartedEpochMs().takeIf { it > 0L }
        ?: if (store.targetKm() > 0 && store.distanceKm() > 0.03 && store.travelMs() > 0L)
            System.currentTimeMillis() - store.travelMs() else null
    private var movingFixCount: Int = 0
    private var lastPersistMs = 0L
    private var recentFixTimeMs = 0L

    var gearInputs by mutableStateOf(store.loadGears()); private set
    var enabledGears by mutableStateOf(GearLogic.validate(gearInputs).bands); private set
    var gearErrors by mutableStateOf(GearLogic.validate(gearInputs).errors); private set
    var currentGear by mutableStateOf<GearBand?>(null); private set
    var speedKmh by mutableStateOf(0.0); private set
    var avgSpeedKmh by mutableStateOf(0.0); private set
    var currentRpm by mutableStateOf(0); private set
    var gpsBars by mutableStateOf(0); private set
    var gpsHasFix by mutableStateOf(false); private set
    var satellitesUsed by mutableStateOf(0); private set
    var targetKm by mutableStateOf(store.targetKm()); private set
    var distanceKm by mutableStateOf(store.distanceKm()); private set
    var elapsedMs by mutableStateOf(
        tripStartEpochMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
    ); private set
    var arrivalMinutes by mutableStateOf<Int?>(null); private set
    var clock by mutableStateOf("--:--"); private set
    var pinnedApps by mutableStateOf(store.selectedPackages()); private set
    var playerPackage by mutableStateOf(store.musicPackage()); private set
    var brightness by mutableStateOf(store.brightness()); private set
    var locationGranted by mutableStateOf(false); private set
    var gpsEnabled by mutableStateOf(false); private set

    private val locationListener = LocationListener { location -> onFix(location) }
    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            satellitesUsed = used
            refreshBars()
        }
        override fun onStopped() { satellitesUsed = 0; gpsBars = 0 }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (tracking) return
        locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        previousLocation = null
        movingFixCount = 0
        if (!locationGranted || !gpsEnabled) { gpsBars = 0; gpsHasFix = false; return }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 750L, 0f, locationListener, Looper.getMainLooper())
            tracking = true
            locationManager.registerGnssStatusCallback(gnssCallback, mainHandler)
        } catch (_: SecurityException) {
            gpsBars = 0
            locationGranted = false
        } catch (_: IllegalArgumentException) { gpsBars = 0 }
    }

    fun stop() {
        if (tracking) runCatching { locationManager.removeUpdates(locationListener) }
        if (tracking) runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
        tracking = false
        // Stopping GPS updates must NOT pause the trip clock.
        movingFixCount = 0
        elapsedMs = tripStartEpochMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
        persistTrip()
        saveBrightness()
        gpsBars = 0; gpsHasFix = false; previousLocation = null; speedKmh=0.0
        currentGear=null; currentRpm=0
    }

    private fun onFix(l: Location) {
        if (!l.hasAccuracy() || l.accuracy > 40f) return
        val now = SystemClock.elapsedRealtime()
        recentFixTimeMs = now
        gpsHasFix = true
        refreshBars()
        val previous = previousLocation
        var newSpeed = if (l.hasSpeed() && l.speed.isFinite()) (l.speed * 3.6).toDouble() else 0.0
        if (!l.hasSpeed() && previous != null) {
            val diffSec = (l.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9
            if (diffSec > 0.3 && diffSec < 12) newSpeed = previous.distanceTo(l) / diffSec * 3.6
        }
        if (newSpeed < 1.5) newSpeed = 0.0
        if (newSpeed !in 0.0..250.0) return // GPS outlier
        speedKmh = if (speedKmh == 0.0) newSpeed else speedKmh * 0.35 + newSpeed * 0.65
        if (speedKmh < 1.5) speedKmh = 0.0

        // Require two consecutive accurate GPS velocity readings (>= 5 km/h)
        // to avoid triggering the timer on stationary position jitter.
        if (targetKm > 0.0 && tripStartEpochMs == null) {
            movingFixCount = if (l.accuracy <= 25f && newSpeed >= 5.0)
                (movingFixCount + 1).coerceAtMost(2) else 0
            if (movingFixCount >= 2) {
                tripStartEpochMs = System.currentTimeMillis()
                elapsedMs = 0L
                persistTrip()
            }
        }

        if (previous != null && targetKm > 0) {
            val dt = (l.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9
            val delta = previous.distanceTo(l).toDouble()
            if (dt in 0.4..12.0 && l.accuracy <= 25f && previous.accuracy <= 25f &&
                delta in 2.5..(dt * 65.0) && newSpeed >= 2.8) {
                distanceKm += delta / 1000.0
            }
        }
        previousLocation = l
        samples.addLast(now to speedKmh)
        while (samples.isNotEmpty() && now - samples.first().first > 90_000L) samples.removeFirst()
        updateCalculatedData()
    }

    private fun refreshBars() {
        gpsBars = if (!gpsHasFix) 0 else when {
            satellitesUsed >= 12 -> 5
            satellitesUsed >= 9 -> 4
            satellitesUsed >= 6 -> 3
            satellitesUsed >= 4 -> 2
            else -> 1
        }
    }

    fun tick() {
        clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        if (gpsHasFix && SystemClock.elapsedRealtime() - recentFixTimeMs > 5000) {
            gpsHasFix = false; gpsBars = 0; speedKmh = 0.0; previousLocation = null
            selector.reset()
            currentGear=null; currentRpm=0
        }
        elapsedMs = tripStartEpochMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
        updateCalculatedData()
        if (SystemClock.elapsedRealtime() - lastPersistMs > 5000) {
            persistTrip(); lastPersistMs = SystemClock.elapsedRealtime()
        }
    }

    private fun updateCalculatedData() {
        currentGear = if (gpsHasFix) selector.select(speedKmh, enabledGears) else null
        currentRpm = currentGear?.rpm(speedKmh) ?: 0
        // Whole-trip average when available; recent GPS samples while getting started.
        val tripAverage = if (elapsedMs >= 15_000 && distanceKm > 0.05)
            distanceKm / (elapsedMs / 3_600_000.0) else 0.0
        val recentAverage = samples.map { it.second }.filter { it > 2.0 }.average().takeIf { !it.isNaN() } ?: 0.0
        avgSpeedKmh = if (tripAverage > 1.0) tripAverage else recentAverage
        val remaining = (targetKm - distanceKm).coerceAtLeast(0.0)
        arrivalMinutes = if (targetKm <= 0 || remaining <= 0) null else
            avgSpeedKmh.takeIf { it >= 3.0 }?.let { ceil(remaining / it * 60.0).toInt() }
    }

    fun setGearField(number: Int, field: Int, value: String) {
        if (value.isNotEmpty() && (value.count { it == '.' } > 1 || value.any { !it.isDigit() && it != '.' })) return
        gearInputs = gearInputs.map { g ->
            if (g.number != number) g else when (field) {
                0 -> g.copy(speedMin = value)
                1 -> g.copy(speedMax = value)
                2 -> g.copy(rpmMin = value)
                else -> g.copy(rpmMax = value)
            }
        }
        store.saveGears(gearInputs) // Save drafts too; invalid ranges simply stay disabled
        val checked = GearLogic.validate(gearInputs)
        enabledGears = checked.bands
        gearErrors = checked.errors
        selector.reset()
        updateCalculatedData()
    }

    fun setTarget(km: Double) {
        targetKm = km.coerceAtLeast(0.0)
        // Entering/editing the route never starts time measurement.
        // Clearing it cancels the trip; an already-started trip otherwise keeps running.
        if (targetKm == 0.0) {
            tripStartEpochMs = null
            movingFixCount = 0
            elapsedMs = 0L
        }
        persistTrip()
        updateCalculatedData()
    }

    fun resetTrip() {
        distanceKm = 0.0; elapsedMs = 0L
        tripStartEpochMs = null // Wait for the NEXT confirmed movement after Reset.
        movingFixCount = 0
        samples.clear()
        previousLocation = null
        persistTrip()
        updateCalculatedData()
    }

    fun setPinned(list: List<String>) {
        pinnedApps = list.distinct().take(8)
        store.saveSelectedPackages(pinnedApps)
    }
    fun selectPlayerPackage(pkg: String) { playerPackage = pkg; store.setMusicPackage(pkg) }
    fun updateBrightness(value: Float) { brightness = value }
    fun saveBrightness() { store.saveBrightness(brightness) }
    private fun persistTrip() = store.saveTrip(targetKm, distanceKm, elapsedMs, tripStartEpochMs)
}
