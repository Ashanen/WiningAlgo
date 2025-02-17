package compute

import model.BollingerBands
import model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object Indicators {

    fun computeRsi(prices: List<Double>, period: Int = 14): List<Double> {
        val rsiList = MutableList(prices.size) { Double.NaN }
        if (prices.size < period) return rsiList

        // Różnice między kolejnymi cenami
        val changes = prices.zipWithNext { prev, curr -> curr - prev }

        // Inicjalizacja sumy zysków/strat
        var gainSum = 0.0
        var lossSum = 0.0

        // Pierwszy okres (period-1 zmian)
        changes.take(period - 1).forEach {
            if (it > 0) gainSum += it else lossSum -= it
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        // Pierwsze RSI w index = period-1
        val firstRsi = if (avgLoss == 0.0) 100.0 else {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }
        rsiList[period - 1] = firstRsi

        // Kolejne wartości RSI
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
                middle[i] = prices.take(i + 1).average()
                upper[i] = middle[i]
                lower[i] = middle[i]
            } else {
                val window = prices.subList(i - period + 1, i + 1)
                val sma = window.average()
                middle[i] = sma
                val sd = sqrt(window.map { (it - sma)*(it - sma) }.average())
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
        val result = MutableList(values.size) { Double.NaN }
        if (period <= 0) return result

        for (i in values.indices) {
            if (i < period - 1) {
                // za mało danych
                continue
            }
            val window = values.subList(i - period + 1, i + 1)
            val avg = window.average()
            result[i] = avg
        }
        return result
    }

    fun computeMaAngle(ma: List<Double>, point: Double = 0.0001): List<Double> {
        val angleList = MutableList(ma.size) { Double.NaN }
        for (i in 1 until ma.size) {
            if (ma[i].isNaN() || ma[i-1].isNaN()) continue
            // Tangens = (Y1 - Y2) / (40 * point)
            val tangens = (ma[i] - ma[i-1]) / (40.0 * point)
            // konwersja na stopnie
            angleList[i] = Math.toDegrees(tangens)
        }
        return angleList
    }

    fun computeAtr(candles: List<Kline>, period: Int = 14): List<Double> {
        // Zwracamy ATR dla każdego indeksu
        val size = candles.size
        val atr = MutableList(size) { Double.NaN }
        if (size < 2) return atr

        // Najpierw obliczamy TR (True Range) dla każdej świecy
        val trList = MutableList(size) { Double.NaN }
        trList[0] = 0.0 // brak poprzedniej świecy

        for (i in 1 until size) {
            val high = candles[i].highPrice.toDoubleOrNull() ?: continue
            val low = candles[i].lowPrice.toDoubleOrNull() ?: continue
            val prevClose = candles[i - 1].closePrice.toDoubleOrNull() ?: continue
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList[i] = tr
        }

        // Teraz liczymy ATR jako EMA(RMA) z TR, period=14
        // Dla uproszczenia – partial ATR. Możesz doprecyzować
        var sumTR = 0.0
        for (i in 0 until period) {
            if (i >= trList.size || trList[i].isNaN()) return atr
            sumTR += trList[i]
        }
        atr[period - 1] = sumTR / period

        for (i in period until size) {
            if (trList[i].isNaN() || atr[i - 1].isNaN()) continue
            val prevAtr = atr[i - 1]
            // ATR = (prevAtr*(period-1) + TR[i]) / period
            val newAtr = ((prevAtr * (period - 1)) + trList[i]) / period
            atr[i] = newAtr
        }

        return atr
    }

}
