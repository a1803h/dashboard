package tj.umed.drivedashboard

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Window
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Android can only REQUEST an available panel mode for our own window.
 * MIUI/HyperOS and the display driver have the final say. This does not
 * change the user's system-wide refresh-rate setting, nor force true 10 Hz
 * on a phone that does not advertise a 10 Hz display mode.
 */
class DisplayRefresh(private val context: Context) {
    companion object {
        /** Auto 60: request 60 Hz while leaving physical mode selection to Android. */
        const val ADAPTIVE_60 = -60
    }

    private fun display(): Display? =
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)

    fun availableHz(): List<Int> {
        val d = display() ?: return emptyList()
        val current = d.mode
        return d.supportedModes
            .asSequence()
            .filter { it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight }
            .map { it.refreshRate.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()
    }

    fun apply(window: Window, requestedHz: Int): Boolean {
        // Unlike choosing a 60 Hz Display.Mode, leave modeId=0 so that the
        // system compositor and LTPO panel remain free to adjust refresh.
        // This is only a hint; HyperOS can ignore it or keep a constant 60 Hz.
        if (requestedHz == ADAPTIVE_60) {
            val attributes = window.attributes
            attributes.preferredDisplayModeId = 0
            attributes.preferredRefreshRate = 60f
            window.attributes = attributes
            return true
        }

        val d = display()
        val chosen = if (requestedHz > 0 && d != null) {
            val currentMode = d.mode
            d.supportedModes
                .filter { it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight }
                .minByOrNull { abs(it.refreshRate - requestedHz) }
                ?.takeIf { abs(it.refreshRate - requestedHz) < 0.8f }
        } else null
        val a = window.attributes
        a.preferredDisplayModeId = chosen?.modeId ?: 0
        a.preferredRefreshRate = chosen?.refreshRate ?: 0f
        window.attributes = a
        return requestedHz == 0 || chosen != null
    }
}
