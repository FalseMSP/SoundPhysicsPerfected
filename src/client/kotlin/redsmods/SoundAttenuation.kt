package redsmods
import kotlin.math.log10

class SoundAttenuation(
    private val sourceDb: Double,   // initial sound level at 1 meter
    private val referenceDistance: Double = 1.0 // default: 1 meter
) {
    fun dbAt(distance: Double): Double {
        require(distance >= referenceDistance) {
            "Distance must be >= reference distance ($referenceDistance m)"
        }

        // Inverse Square Law (spherical spreading): 20 * log10(d / d0)
        val attenuation = 20 * log10(distance / referenceDistance)
        return sourceDb - attenuation
    }
}
