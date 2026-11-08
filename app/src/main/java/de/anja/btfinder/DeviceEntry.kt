package de.anja.btfinder

import android.os.SystemClock

/** Ein gesehenes BLE-Gerät. RSSI wird geglättet, weil der Rohwert stark springt. */
class DeviceEntry(val address: String) {
    var name: String? = null
    var manufacturerId: Int? = null
    var rssi: Float = -100f
        private set
    var lastSeen: Long = 0L
        private set
    private var first = true

    fun update(newName: String?, mfr: Int?, rawRssi: Int) {
        if (!newName.isNullOrBlank()) name = newName
        if (mfr != null) manufacturerId = mfr
        rssi = if (first) rawRssi.toFloat() else rssi + ALPHA * (rawRssi - rssi)
        first = false
        lastSeen = SystemClock.elapsedRealtime()
    }

    fun ageMs(): Long = SystemClock.elapsedRealtime() - lastSeen

    fun manufacturerLabel(): String? = manufacturerId?.let { KNOWN[it] ?: "Hersteller 0x%04X".format(it) }

    companion object {
        private const val ALPHA = 0.3f
        private val KNOWN = mapOf(
            0x004C to "Apple",
            0x0075 to "Samsung",
            0x0006 to "Microsoft",
            0x00E0 to "Google",
            0x012D to "Sony",
            0x009E to "Bose",
            0x0087 to "Garmin",
        )
    }
}
