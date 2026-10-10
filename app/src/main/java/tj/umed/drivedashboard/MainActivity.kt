package tj.umed.drivedashboard

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle

class MainActivity : ComponentActivity() {
    private lateinit var dashboard: DashboardController
    private lateinit var media: MediaBridge
    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) dashboard.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dashboard = DashboardController(this)
        media = MediaBridge(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setDeviceBrightness(dashboard.brightness)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                        onBack = { showSettings = false }
                    )
                } else {
                    DashboardScreen(
                        controller = dashboard,
                        media = media,
                        audio = audio,
                        onSettings = { showSettings = true },
                        onRequestGps = { requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        onMediaPermission = { startActivity(media.notificationPermissionIntent()) },
                        onBrightness = { value ->
                            dashboard.setBrightness(value)
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
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        if (::media.isInitialized) media.refresh(dashboard.playerPackage)
    }
    override fun onPause() {
        if (::dashboard.isInitialized) dashboard.stop()
        super.onPause()
    }
}
