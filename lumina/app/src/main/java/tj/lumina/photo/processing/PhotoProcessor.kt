package tj.lumina.photo.processing

import android.graphics.Bitmap
import android.graphics.Color
import tj.lumina.photo.model.EditSettings
import tj.lumina.photo.model.FilmPreset
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A deterministic, on-device 8-bit RGB photo processor.
 * Never inserts, removes or hallucinates objects. Keeps pixel coordinates unchanged.
 * The photo is never transmitted off-device.
 *
 * Processing is intentionally CPU-based in v0.1. Future versions can use a
 * GPU shader/tiling pipeline for full 50MP output and smoother previews.
 */
object PhotoProcessor {
    fun render(input: Bitmap, preset: FilmPreset, settings: EditSettings): Bitmap {
        val source = input.copy(Bitmap.Config.ARGB_8888, false)
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val strength = settings.intensity.coerceIn(0f, 1f)
        if (strength <= 0f) return source

        val contrast = (preset.contrast + settings.contrast * 0.26f).coerceIn(-0.8f, 0.8f)
        val saturation = (1f + preset.saturation + settings.saturation * 0.50f).coerceIn(0f, 2.3f)
        val warmth = (preset.warmth + settings.warmth * 0.20f).coerceIn(-0.5f, 0.5f)
        val fade = (preset.fade + settings.fade * 0.25f).coerceIn(0f, 0.45f)
        val grain = (preset.grain * (0.35f + settings.grain * 1.3f)).coerceIn(0f, 0.7f)
        val gamma = preset.gamma
        val shadowBlue = preset.shadowBlue
        val highlightWarmth = preset.highlightWarmth
        val multiplier = 1f + contrast * 1.4f
        // Perceptual gamma LUT saves millions of pow() calculations on big photos.
        val gammaLut = FloatArray(256) { i -> (i / 255f).pow(gamma) }

        for (i in pixels.indices) {
            val p = pixels[i]
            val origR = Color.red(p) / 255f
            val origG = Color.green(p) / 255f
            val origB = Color.blue(p) / 255f
            var r = gammaLut[Color.red(p)]
            var g = gammaLut[Color.green(p)]
            var b = gammaLut[Color.blue(p)]
            val y = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)

            // Luma-preserving saturation, then toe/shoulder and color separation.
            r = y + (r - y) * saturation
            g = y + (g - y) * saturation
            b = y + (b - y) * saturation
            r = (r - 0.5f) * multiplier + 0.5f
            g = (g - 0.5f) * multiplier + 0.5f
            b = (b - 0.5f) * multiplier + 0.5f

            val highlights = (y * y).coerceIn(0f, 1f)
            val shadows = ((1f - y) * (1f - y)).coerceIn(0f, 1f)
            r += warmth * 0.24f + highlightWarmth * highlights * 0.16f - shadowBlue * shadows * 0.07f
            g += warmth * 0.024f + highlightWarmth * highlights * 0.03f
            b -= warmth * 0.18f + highlightWarmth * highlights * 0.11f
            b += shadowBlue * shadows * 0.23f

            r = r * (1f - fade) + (0.13f + y * 0.66f) * fade
            g = g * (1f - fade) + (0.13f + y * 0.66f) * fade
            b = b * (1f - fade) + (0.13f + y * 0.66f) * fade

            // Position-based grain, identical on every edit/export for each pixel.
            val n = if (grain > 0f) (hashNoise(i, width) - 0.5f) * grain * 0.125f else 0f
            r += n; g += n; b += n

            val outR = origR + (r - origR) * strength
            val outG = origG + (g - origG) * strength
            val outB = origB + (b - origB) * strength
            pixels[i] = Color.argb(
                Color.alpha(p), (outR.coerceIn(0f, 1f) * 255f).roundToInt(),
                (outG.coerceIn(0f, 1f) * 255f).roundToInt(),
                (outB.coerceIn(0f, 1f) * 255f).roundToInt()
            )
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        if (source !== input) source.recycle()
        return output
    }

    private fun hashNoise(i: Int, width: Int): Float {
        var x = (i + width * 7919) * 0x45d9f3b
        x = x xor (x ushr 16)
        x *= 0x45d9f3b
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535f
    }
}
