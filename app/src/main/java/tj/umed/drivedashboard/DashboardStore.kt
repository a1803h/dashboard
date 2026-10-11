package tj.umed.drivedashboard

import android.content.Context
import org.json.JSONArray

class DashboardStore(context: Context) {
    private val prefs = context.getSharedPreferences("drive_dashboard_v1", Context.MODE_PRIVATE)

    fun loadGears(): List<GearInput> = GearLogic.defaults().map { default ->
        val prefix = "gear_${default.number}_"
        GearInput(
            number = default.number,
            speedMin = prefs.getString(prefix + "smin", default.speedMin) ?: "",
            speedMax = prefs.getString(prefix + "smax", default.speedMax) ?: "",
            rpmMin = prefs.getString(prefix + "rmin", default.rpmMin) ?: "",
            rpmMax = prefs.getString(prefix + "rmax", default.rpmMax) ?: ""
        )
    }

    fun saveGears(items: List<GearInput>) {
        prefs.edit().apply {
            items.forEach { g ->
                val p = "gear_${g.number}_"
                putString(p + "smin", g.speedMin)
                putString(p + "smax", g.speedMax)
                putString(p + "rmin", g.rpmMin)
                putString(p + "rmax", g.rpmMax)
            }
        }.apply()
    }

    fun selectedPackages(): List<String> = runCatching {
        val json = JSONArray(prefs.getString("pinned_apps", "[]"))
        (0 until json.length()).map { json.getString(it) }.distinct().take(8)
    }.getOrDefault(emptyList())

    fun saveSelectedPackages(pkgs: List<String>) {
        prefs.edit().putString("pinned_apps", JSONArray(pkgs.distinct().take(8)).toString()).apply()
    }

    fun musicPackage(): String = prefs.getString("music_package", "") ?: ""
    fun setMusicPackage(value: String) = prefs.edit().putString("music_package", value).apply()

    fun targetKm(): Double = prefs.getFloat("trip_total", 0f).toDouble()
    fun distanceKm(): Double = prefs.getFloat("trip_distance", 0f).toDouble()
    fun travelMs(): Long = prefs.getLong("trip_elapsed", 0L)
    /** Epoch of the first confirmed driving movement; zero means not started yet. */
    fun tripStartedEpochMs(): Long = prefs.getLong("trip_started_epoch_ms", 0L)
    fun saveTrip(target: Double, distance: Double, duration: Long, startedEpochMs: Long?) {
        prefs.edit().putFloat("trip_total", target.toFloat())
            .putFloat("trip_distance", distance.toFloat())
            .putLong("trip_elapsed", duration)
            .putLong("trip_started_epoch_ms", startedEpochMs ?: 0L).apply()
    }
    /** 0 means Android chooses automatically. Display modes are device-dependent. */
    fun refreshRateHz(): Int = prefs.getInt("preferred_refresh_rate_hz", 0)
    fun saveRefreshRateHz(hz: Int) = prefs.edit()
        .putInt("preferred_refresh_rate_hz", hz.coerceAtLeast(0)).apply()
    fun brightness(): Float = prefs.getFloat("brightness", 0.70f)
    fun saveBrightness(value: Float) = prefs.edit().putFloat("brightness", value).apply()
}
