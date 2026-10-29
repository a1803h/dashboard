package tj.umed.drivedashboard

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.WindowManager

/**
 * Ask for the 60 Hz ceiling / system-adaptive refresh policy.
 * A refresh preference alone is NOT a hard 10-60 Hz range guarantee:
 * the panel's actual variable refresh behavior is managed by HyperOS.
 */
class AdaptiveDisplay(private val activity: Activity) {
    private var savedPreferredRate: Float? = null
    private var active = false

    private fun notifyXiaomiPowerKeeper(restore: Boolean) {
        if (!Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) return
        // Action/extras observed in Xiaomi Camera 6.1; permission checks are device-dependent.
        runCatching {
            val intent = Intent("com.miui.powerkeeper.set_fps").apply {
                setPackage("com.miui.powerkeeper")
                putExtra("pkg", activity.packageName)
                putExtra("uid", activity.applicationInfo.uid)
                putExtra("isRestore", restore)
            }
            activity.sendBroadcast(intent)
        }
    }

    fun start() {
        if (active) return
        val attributes = activity.window.attributes
        savedPreferredRate = attributes.preferredRefreshRate
        attributes.preferredRefreshRate = 60f
        activity.window.attributes = attributes
        notifyXiaomiPowerKeeper(restore = false)
        active = true
    }

    fun restore() {
        if (!active) return
        notifyXiaomiPowerKeeper(restore = true)
        val attributes = activity.window.attributes
        attributes.preferredRefreshRate = savedPreferredRate ?: 0f
        activity.window.attributes = attributes
        savedPreferredRate = null
        active = false
    }
}
