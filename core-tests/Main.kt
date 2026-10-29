import tj.umed.drivedashboard.*

fun main() {
    val base = GearLogic.validate(GearLogic.defaults())
    check(base.errors.isEmpty()) { base.errors }
    check(base.bands.size == 5)
    check(GearLogic.defaults().last().isBlank())
    val selector = GearSelector()
    check(selector.select(20.0, base.bands)?.number == 1)
    check(selector.select(30.5, base.bands)?.number == 1)
    check(selector.select(31.0, base.bands)?.number == 2)
    check(selector.select(47.0, base.bands)?.number == 3)
    check(selector.select(64.0, base.bands)?.number == 4)
    check(selector.select(65.0, base.bands)?.rpm(65.0) == 2063)
    check(selector.select(80.0, base.bands)?.number == 5)
    check(selector.select(79.0, base.bands)?.number == 5) // no flicker
    check(selector.select(78.4, base.bands)?.number == 4) // downshift after -1.5
    val six = GearLogic.defaults().toMutableList()
    six[5] = GearInput(6, "120", "160", "2000", "3000")
    check(GearLogic.validate(six).bands.size == 6)
    six[5] = GearInput(6, "120", "160", "", "3000")
    check(GearLogic.validate(six).bands.size == 5)
    check(GearLogic.validate(six).errors.containsKey(6))
    six[5] = GearInput(6, "110", "160", "2000", "3000")
    check(GearLogic.validate(six).bands.size == 5)
    val detector = DrivingDetector()
    detector.observe(4.5, 0L)
    detector.observe(4.5, 13_000L)
    check(detector.movement == Movement.WALKING) // walking up to 5 km/h
    detector.observe(5.0, 14_000L)
    check(detector.movement == Movement.WALKING) // exactly 5 is still walking
    detector.observe(5.1, 20_000L)
    check(detector.movement == Movement.WALKING) // crossing boundary does not instantly start trip
    detector.observe(5.1, 26_000L)
    check(detector.movement == Movement.DRIVING)
    detector.detectedWalking()
    check(detector.movement == Movement.WALKING)
    detector.detectedVehicle()
    check(detector.movement == Movement.DRIVING)
    detector.reset()
    check(detector.movement == Movement.WAITING)
    println("All gear and driving detector tests passed.")
}
