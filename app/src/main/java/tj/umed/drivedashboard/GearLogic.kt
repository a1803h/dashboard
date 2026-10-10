package tj.umed.drivedashboard

import kotlin.math.roundToInt

/** A deliberately *suggested* gear, never a claim about the physical gearbox. */
data class GearInput(
    val number: Int,
    val speedMin: String = "",
    val speedMax: String = "",
    val rpmMin: String = "",
    val rpmMax: String = ""
) {
    fun values() = listOf(speedMin, speedMax, rpmMin, rpmMax)
    fun isBlank() = values().all { it.isBlank() }
}

data class GearBand(
    val number: Int,
    val speedMin: Double,
    val speedMax: Double,
    val rpmMin: Double,
    val rpmMax: Double
) {
    fun rpm(speedKmh: Double): Int {
        val ratio = (speedKmh.coerceAtLeast(speedMin) - speedMin) / (speedMax - speedMin)
        return (rpmMin + ratio * (rpmMax - rpmMin)).coerceIn(0.0, 8000.0).roundToInt()
    }
}

data class GearValidation(val bands: List<GearBand>, val errors: Map<Int, String>)

object GearLogic {
    fun defaults(): List<GearInput> = listOf(
        GearInput(1, "0", "30", "0", "3000"),
        GearInput(2, "31", "47", "2000", "3000"),
        GearInput(3, "47", "64", "2000", "3000"),
        GearInput(4, "64", "80", "2000", "3000"),
        GearInput(5, "80", "120", "2000", "3000"),
        GearInput(6)
    )

    /** Empty gears are disabled; incomplete or invalid gears are disabled + annotated. */
    fun validate(input: List<GearInput>): GearValidation {
        val active = mutableListOf<GearBand>()
        val errors = mutableMapOf<Int, String>()
        for (item in input.sortedBy { it.number }) {
            if (item.isBlank()) continue
            if (item.values().any { it.isBlank() }) {
                errors[item.number] = "Заполните все 4 поля — пока передача выключена"
                continue
            }
            val a = item.speedMin.trim().toDoubleOrNull()
            val b = item.speedMax.trim().toDoubleOrNull()
            val c = item.rpmMin.trim().toDoubleOrNull()
            val d = item.rpmMax.trim().toDoubleOrNull()
            if (listOf(a, b, c, d).any { it == null || !it.isFinite() } ||
                a!! < 0 || b!! <= a || c!! < 0 || d!! < c) {
                errors[item.number] = "Проверьте min/max скорости и оборотов"
                continue
            }
            if (active.any { a < it.speedMax && b > it.speedMin }) {
                // A shared boundary (e.g. 47–47) is fine, larger overlap is ambiguous.
                errors[item.number] = "Диапазон скорости пересекается с другой передачей"
                continue
            }
            active += GearBand(item.number, a, b, c, d)
        }
        return GearValidation(active.sortedWith(compareBy<GearBand> { it.speedMin }.thenBy { it.number }), errors)
    }
}

class GearSelector(private val downshiftHysteresisKmh: Double = 1.5) {
    private var previousGear: Int? = null

    fun reset() { previousGear = null }

    /** Upshift at the configured threshold; downshift only below threshold - hysteresis. */
    fun select(speedKmh: Double, available: List<GearBand>): GearBand? {
        if (available.isEmpty()) { reset(); return null }
        val bands = available.sortedWith(compareBy<GearBand> { it.speedMin }.thenBy { it.number })
        val speed = speedKmh.coerceAtLeast(0.0)
        val ideal = bands.lastOrNull { speed >= it.speedMin } ?: bands.first()
        val prior = bands.firstOrNull { it.number == previousGear }
        val selected = when {
            prior == null -> ideal
            ideal.speedMin > prior.speedMin -> ideal
            ideal.speedMin < prior.speedMin && speed < prior.speedMin - downshiftHysteresisKmh -> ideal
            else -> prior
        }
        previousGear = selected.number
        return selected
    }
}
