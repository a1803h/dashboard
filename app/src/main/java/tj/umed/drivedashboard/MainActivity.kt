package tj.umed.drivedashboard

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    private lateinit var dashboard: DashboardController
    private lateinit var media: MediaBridge
    private lateinit var displayRefresh: DisplayRefresh
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) dashboard.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboard = DashboardController(this)
        media = MediaBridge(this)
        displayRefresh = DisplayRefresh(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setDeviceBrightness(dashboard.brightness)
        displayRefresh.apply(window, dashboard.refreshRateHz)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        setContent {
            // Scale UI dimensions and MiSans glyphs from Figma consistently.
            // System font magnification otherwise clips text in fixed-height Figma rows.
            val systemDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(systemDensity.density, fontScale = 1f)) {
                MaterialTheme(colorScheme = darkColorScheme(), typography = MiSansTypography) {
                var showSettings by remember { mutableStateOf(false) }
                BackHandler(enabled = showSettings) { showSettings = false }
                LaunchedEffect(Unit) {
                    while (true) {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            dashboard.tick()
                            media.refresh(dashboard.playerPackage)
                        }
                        delay(1000)
                    }
                }
                if (showSettings) {
                    SettingsScreen(
                        controller = dashboard,
                        onBack = { showSettings = false },
                        availableRefreshRates = displayRefresh.availableHz(),
                        onRefreshRateChange = { rate ->
                            dashboard.selectRefreshRate(rate)
                            displayRefresh.apply(window, rate)
                        }
                    )
                } else {
                    DashboardScreen(
                        controller = dashboard,
                        media = media,
                        audio = audio,
                        onSettings = { showSettings = true },
                        onRequestGps = ::requestGpsAccess,
                        onGpsRescan = ::rescanGpsFromSpeed,
                        onMediaPermission = { startActivity(media.notificationPermissionIntent()) },
                        onBrightness = { value ->
                            dashboard.updateBrightness(value)
                            setDeviceBrightness(value)
                        },
                        onOpenApp = { pkg ->
                            runCatching {
                                packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity)
                            }
                        }
                    )
                }
              }
            }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    private fun rescanGpsFromSpeed() {
        val message = dashboard.rescanGps()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestGpsAccess() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val location = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!location.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            dashboard.start()
        }
    }

    private fun setDeviceBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value.coerceIn(0.02f, 1f) // Only this window; no WRITE_SETTINGS permission
        window.attributes = params
    }
    override fun onResume() {
        super.onResume()
        if (::dashboard.isInitialized) dashboard.start()
        if (::displayRefresh.isInitialized && ::dashboard.isInitialized) {
            displayRefresh.apply(window, dashboard.refreshRateHz)
        }
        if (::media.isInitialized) media.refresh(dashboard.playerPackage)
    }
    override fun onPause() {
        if (::dashboard.isInitialized) dashboard.stop()
        super.onPause()
    }
}
