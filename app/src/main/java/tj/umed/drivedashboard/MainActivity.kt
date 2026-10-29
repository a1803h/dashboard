package tj.umed.drivedashboard

import android.Manifest
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
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var dashboard: DashboardController

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) dashboard.start() }

    private val recognitionPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { dashboard.start() }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
             View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboard = DashboardController(this)
        hideBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(dashboard.brightness)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(), typography = MiSansTypography) {
                var settings by remember { mutableStateOf(false) }
                BackHandler(enabled = settings) { settings = false }
                LaunchedEffect(Unit) {
                    while (true) {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            dashboard.tick()
                            hideBars()
                        }
                        delay(1000L)
                    }
                }
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
            dashboard.start()
        }
    }

    private fun applyBrightness(value: Float) {
        val attrs = window.attributes
        attrs.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = attrs
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (::dashboard.isInitialized) dashboard.start()
    }

    override fun onPause() {
        if (::dashboard.isInitialized) dashboard.stop()
        super.onPause()
    }
}
