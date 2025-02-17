package strategy

import compute.Indicators
import model.Kline

enum class Trend {
    UP, DOWN, FLAT
}

class TrendDetector(
    private val shortMaPeriod: Int = 20,
    private val longMaPeriod: Int = 50,
    private val flatThreshold: Double = 0.001
) {
    /**
     * Określa trend na podstawie dwóch średnich (EMA).
     * Dodatkowo wprowadza "flatThreshold", by zdefiniować strefę neutralną (FLAT).
     */
    fun detectTrend(candles: List<Kline>): Trend {
        if (candles.size < longMaPeriod) return Trend.FLAT

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < longMaPeriod) return Trend.FLAT

        // Używamy computeEma (poniżej w Indicators jest implementacja)
        val emaShort = Indicators.computeEma(closeList, shortMaPeriod)
        val emaLong = Indicators.computeEma(closeList, longMaPeriod)

        // Bierzemy ostatnie wartości
        val shortVal = emaShort.lastOrNull() ?: return Trend.FLAT
        val longVal = emaLong.lastOrNull() ?: return Trend.FLAT

        val diff = shortVal - longVal
        return when {
            diff > flatThreshold -> Trend.UP
            diff < -flatThreshold -> Trend.DOWN
            else -> Trend.FLAT
        }
    }
}
