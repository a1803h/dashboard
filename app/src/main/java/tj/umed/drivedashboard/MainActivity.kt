package tj.umed.drivedashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    private lateinit var dashboard: DashboardController
    private lateinit var adaptiveDisplay: AdaptiveDisplay

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) askForPermissions()
    }

    private val recognitionPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        askForNotificationPermission()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startTripService()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
             View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // State belongs to the process-level controller, not to this activity's lifecycle.
        dashboard = TripRuntime.get(applicationContext)
        adaptiveDisplay = AdaptiveDisplay(this)
        hideBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(dashboard.brightness)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(), typography = MiSansTypography) {
                var settings by remember { mutableStateOf(false) }
                BackHandler(enabled = settings) { settings = false }
                if (settings) {
                    SettingsScreen(controller = dashboard, onBack = { settings = false })
                } else {
                    DashboardScreen(
                        controller = dashboard,
                        onSettings = { settings = true },
                        onRequestGps = { askForPermissions() },
                        onBrightness = {
                            dashboard.updateBrightness(it)
                            applyBrightness(it)
                        },
                        onOpenApp = { packageName ->
                            runCatching {
                                packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                            }
                        }
                    )
                }
            }
        }
        askForPermissions()
    }

    private fun askForPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            recognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            askForNotificationPermission()
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startTripService()
        }
    }

    /** Must initiate a location FGS while our activity is visible (Android 14+). */
    private fun startTripService() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, TripTrackingService::class.java).setAction(TripTrackingService.ACTION_START)
        runCatching { startForegroundService(intent) }
    }

    private fun applyBrightness(value: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = attrs
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (::adaptiveDisplay.isInitialized) adaptiveDisplay.start()
        // Returning to the dashboard re-establishes the service if the system killed it.
        // startForegroundService is idempotent for an already running service.
        if (::dashboard.isInitialized &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startTripService()
        }
    }

    override fun onPause() {
        // Deliberately do not stop the GPS controller: TripTrackingService owns its lifecycle.
        if (::adaptiveDisplay.isInitialized) adaptiveDisplay.restore()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }
}
