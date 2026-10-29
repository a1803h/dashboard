package tj.umed.drivedashboard

/** GPS fallback for Activity Recognition. Over 5 km/h sustained indicates driving. */
enum class Movement { WAITING, DRIVING, WALKING }

class DrivingDetector {
    var movement: Movement = Movement.WAITING
        private set
    private var fastSince: Long? = null
    private var walkSince: Long? = null

    fun observe(speedKmh: Double, nowMs: Long) {
        if (speedKmh > 5.0) {
            if (fastSince == null) fastSince = nowMs
            walkSince = null
            if (nowMs - (fastSince ?: nowMs) >= 5500L) movement = Movement.DRIVING
        } else if (speedKmh in 1.5..5.0) {
            if (walkSince == null) walkSince = nowMs
            fastSince = null
            if (nowMs - (walkSince ?: nowMs) >= 12000L) movement = Movement.WALKING
        } else {
            fastSince = null
            walkSince = null
            // Do not pause at red lights or in traffic jams.
        }
    }

    fun detectedVehicle() {
        movement = Movement.DRIVING
        fastSince = null
        walkSince = null
    }

    fun detectedWalking() {
        movement = Movement.WALKING
        fastSince = null
        walkSince = null
    }

    fun reset() {
        movement = Movement.WAITING
        fastSince = null
        walkSince = null
    }
}
