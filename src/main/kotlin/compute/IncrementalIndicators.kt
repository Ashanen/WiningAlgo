package compute

import model.Kline
import kotlin.math.abs
import kotlin.math.max

class IncrementalIndicators(private val atrPeriod: Int = 14) {
    private var previousClose: Double? = null
    private var currentAtr: Double? = null
    private val trHistory = mutableListOf<Double>()

    fun updateAtr(kline: Kline): Double? {
        val high = kline.highPrice.toDoubleOrNull() ?: return null
        val low = kline.lowPrice.toDoubleOrNull() ?: return null
        val close = kline.closePrice.toDoubleOrNull() ?: return null

        if (previousClose == null) {
            previousClose = close
            return null
        }

        val tr = max(high - low, max(abs(high - previousClose!!), abs(low - previousClose!!)))
        trHistory.add(tr)
        if (trHistory.size < atrPeriod) return null

        if (trHistory.size == atrPeriod && currentAtr == null) {
            currentAtr = trHistory.average()
        } else if (trHistory.size > atrPeriod) {
            currentAtr = (currentAtr!! * (atrPeriod - 1) + tr) / atrPeriod
            trHistory.removeAt(0)
        }
        previousClose = close
        return currentAtr
    }
}