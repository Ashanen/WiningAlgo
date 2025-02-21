package compute

import model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.Double.Companion.NaN

data class BollingerBands(
    val middle: List<Double>,
    val upper: List<Double>,
    val lower: List<Double>
)

object Indicators {

    fun computeRsi(prices: List<Double>, period: Int = 14): List<Double> {
        val rsiList = MutableList(prices.size) { NaN }
        if (prices.size < period) return rsiList

        val changes = prices.zipWithNext { prev, curr -> curr - prev }
        var gainSum = 0.0
        var lossSum = 0.0

        changes.take(period - 1).forEach {
            if (it > 0) gainSum += it else lossSum -= it
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        val firstRsi = if (avgLoss == 0.0) 100.0 else {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }
        rsiList[period - 1] = firstRsi

        for (i in period until prices.size) {
            val diff = changes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0

            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period

            val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
            val rsi = 100.0 - (100.0 / (1.0 + rs))
            rsiList[i] = rsi
        }
        return rsiList
    }

    fun computeBollingerBands(prices: List<Double>, period: Int, multiplier: Double): BollingerBands {
        val middle = MutableList(prices.size) { 0.0 }
        val upper = MutableList(prices.size) { 0.0 }
        val lower = MutableList(prices.size) { 0.0 }

        for (i in prices.indices) {
            if (i < period - 1) {
                val avg = prices.take(i + 1).average()
                middle[i] = avg
                upper[i] = avg
                lower[i] = avg
            } else {
                val window = prices.subList(i - period + 1, i + 1)
                val sma = window.average()
                middle[i] = sma
                val sd = sqrt(window.map { (it - sma) * (it - sma) }.average())
                upper[i] = sma + multiplier * sd
                lower[i] = sma - multiplier * sd
            }
        }
        return BollingerBands(middle, upper, lower)
    }

    fun calculateATR(candles: List<Kline>): Double {
        if (candles.size < 2) return 0.0
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val high = candles[i].highPrice.toDoubleOrNull() ?: continue
            val low = candles[i].lowPrice.toDoubleOrNull() ?: continue
            val prevClose = candles[i - 1].closePrice.toDoubleOrNull() ?: continue
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trueRanges.add(tr)
        }
        return if (trueRanges.isNotEmpty()) trueRanges.average() else 0.0
    }

    fun computeSma(values: List<Double>, period: Int): List<Double> {
        val result = MutableList(values.size) { NaN }
        if (period <= 0) return result
        for (i in values.indices) {
            if (i < period - 1) continue
            val window = values.subList(i - period + 1, i + 1)
            val avg = window.average()
            result[i] = avg
        }
        return result
    }

    fun computeMaAngle(ma: List<Double>, point: Double = 0.0001): List<Double> {
        val angleList = MutableList(ma.size) { NaN }
        for (i in 1 until ma.size) {
            if (ma[i].isNaN() || ma[i-1].isNaN()) continue
            val tangens = (ma[i] - ma[i-1]) / (40.0 * point)
            angleList[i] = Math.toDegrees(tangens)
        }
        return angleList
    }

    fun computeAtr(candles: List<Kline>, period: Int = 14): List<Double> {
        val size = candles.size
        val atr = MutableList(size) { NaN }
        if (size < 2) return atr

        val trList = MutableList(size) { NaN }
        trList[0] = 0.0
        for (i in 1 until size) {
            val high = candles[i].highPrice.toDoubleOrNull() ?: continue
            val low = candles[i].lowPrice.toDoubleOrNull() ?: continue
            val prevClose = candles[i - 1].closePrice.toDoubleOrNull() ?: continue
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList[i] = tr
        }
        if (period > size) return atr

        var sumTR = 0.0
        for (i in 0 until period) {
            if (trList[i].isNaN()) return atr
            sumTR += trList[i]
        }
        atr[period - 1] = sumTR / period

        for (i in period until size) {
            if (trList[i].isNaN() || atr[i - 1].isNaN()) continue
            val prevAtr = atr[i - 1]
            val newAtr = ((prevAtr * (period - 1)) + trList[i]) / period
            atr[i] = newAtr
        }
        return atr
    }

    fun computeEma(prices: List<Double>, period: Int): List<Double> {
        val result = MutableList(prices.size) { Double.NaN }
        if (prices.isEmpty() || period <= 0) return result

        val multiplier = 2.0 / (period + 1)
        var prevEma = prices.first()
        result[0] = prevEma

        for (i in 1 until prices.size) {
            val price = prices[i]
            val ema = (price - prevEma) * multiplier + prevEma
            result[i] = ema
            prevEma = ema
        }
        return result
    }
}
