package tj.umed.drivedashboard

import kotlin.math.roundToInt

/**
 * Numeric labels inside the Figma tiles must never exceed three characters.
 * Units and original measurements are unaffected by the display-only cap.
 */
object TileNumberFormatter {
    fun kilometers(value: Double): String {
        if (!value.isFinite() || value <= 0.0) return "000"
        if (value < 10.0) {
            // Round to tenths only below 10; 9.96 becomes "10" (never "10.0").
            val tenths = (value * 10.0).roundToInt()
            if (tenths in 1..99) {
                val whole = tenths / 10
                val fraction = tenths % 10
                return if (fraction == 0) whole.toString() else "$whole.$fraction"
            }
            if (tenths == 0) return "000"
        }
        // No space for 1000+ within three characters; saturate the indicator at 999.
        return value.roundToInt().coerceAtMost(999).toString()
    }

    fun integer(value: Int): String = if (value <= 0) "000" else value.coerceAtMost(999).toString()
}
