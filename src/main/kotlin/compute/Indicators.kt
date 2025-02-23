package compute

import model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.Double.Companion.NaN

object Indicators {

    // Struktury danych dla wyników
    data class MacdResult(val macdLine: List<Double>, val signalLine: List<Double>, val histogram: List<Double>)
    data class BollingerBandsResult(val middle: List<Double>, val upper: List<Double>, val lower: List<Double>)

    // Funkcja pomocnicza do obliczania SMA (Simple Moving Average)
    private fun computeSma(prices: List<Double>, period: Int): List<Double> {
        return (period until prices.size).map { i ->
            prices.subList(i - period, i).average()
        }
    }

    // **EMA (Exponential Moving Average)**
    fun computeEma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1) // Współczynnik wygładzania
        val ema = mutableListOf(prices.first()) // Pierwsza wartość to cena początkowa
        for (i in 1 until prices.size) {
            val currentEma = prices[i] * k + ema.last() * (1 - k)
            ema.add(currentEma)
        }
        return ema
    }

    // **MACD (Moving Average Convergence Divergence)**
    fun computeMacd(prices: List<Double>, fastPeriod: Int, slowPeriod: Int, signalPeriod: Int): MacdResult {
        val fastEma = computeEma(prices, fastPeriod) // Szybka EMA
        val slowEma = computeEma(prices, slowPeriod) // Wolna EMA
        val macdLine = fastEma.zip(slowEma) { f, s -> f - s } // MACD Line = szybka EMA - wolna EMA
        val signalLine = computeEma(macdLine, signalPeriod) // Signal Line = EMA z MACD Line
        val histogram = macdLine.zip(signalLine) { m, s -> m - s } // Histogram = MACD Line - Signal Line
        return MacdResult(macdLine, signalLine, histogram)
    }

    // **RSI (Relative Strength Index)**
    fun computeRsi(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period + 1) return emptyList()
        val changes = prices.zipWithNext { a, b -> b - a } // Zmiany cenowe
        val gains = changes.map { if (it > 0) it else 0.0 } // Zyski
        val losses = changes.map { if (it < 0) -it else 0.0 } // Straty

        var avgGain = gains.subList(0, period).average() // Początkowa średnia zysków
        var avgLoss = losses.subList(0, period).average() // Początkowa średnia strat
        val rsiList = mutableListOf<Double>()

        // Pierwsze RSI
        val firstRsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        rsiList.add(firstRsi)

        // Kolejne wartości RSI
        for (i in period until changes.size) {
            val currentGain = if (changes[i] > 0) changes[i] else 0.0
            val currentLoss = if (changes[i] < 0) -changes[i] else 0.0
            avgGain = (avgGain * (period - 1) + currentGain) / period
            avgLoss = (avgLoss * (period - 1) + currentLoss) / period
            val rsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
            rsiList.add(rsi)
        }
        return rsiList
    }

    // **ATR (Average True Range)**
    fun computeAtr(klines: List<Kline>, period: Int): List<Double> {
        if (klines.size < period + 1) return emptyList()
        val trList = mutableListOf<Double>()
        for (i in 1 until klines.size) {
            val high = klines[i].highPrice.toDouble()
            val low = klines[i].lowPrice.toDouble()
            val prevClose = klines[i - 1].closePrice.toDouble()
            val tr = maxOf(high - low, abs(high - prevClose), abs(low - prevClose)) // True Range
            trList.add(tr)
        }
        val atrList = mutableListOf<Double>()
        var currentAtr = trList.subList(0, period).average() // Początkowy ATR
        atrList.add(currentAtr)
        for (i in period until trList.size) {
            currentAtr = (currentAtr * (period - 1) + trList[i]) / period // Wygładzona średnia
            atrList.add(currentAtr)
        }
        return atrList
    }

    // **Bollinger Bands**
    fun computeBollingerBands(prices: List<Double>, period: Int, numDevs: Double): BollingerBandsResult {
        if (prices.size < period) return BollingerBandsResult(emptyList(), emptyList(), emptyList())
        val sma = computeSma(prices, period) // Środkowa wstęga (SMA)
        val stdDev = (period until prices.size).map { i ->
            val sublist = prices.subList(i - period, i)
            val mean = sublist.average()
            sqrt(sublist.map { (it - mean) * (it - mean) }.sum() / period) // Odchylenie standardowe
        }
        val upper = sma.zip(stdDev) { m, s -> m + numDevs * s } // Górna wstęga
        val lower = sma.zip(stdDev) { m, s -> m - numDevs * s } // Dolna wstęga
        return BollingerBandsResult(sma, upper, lower)
    }
}