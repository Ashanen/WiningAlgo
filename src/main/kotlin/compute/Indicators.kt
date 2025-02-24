package compute

import model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.slf4j.LoggerFactory

// Struktury danych dla wyników wskaźników
data class MacdResult(val macdLine: List<Double>, val signalLine: List<Double>, val histogram: List<Double>)
data class BollingerBandsResult(val middle: List<Double>, val upper: List<Double>, val lower: List<Double>)
data class StochasticResult(val k: List<Double>, val d: List<Double>)
data class IchimokuResult(
    val tenkanSen: List<Double>,
    val kijunSen: List<Double>,
    val senkouSpanA: List<Double>,
    val senkouSpanB: List<Double>,
    val chikouSpan: List<Double>
)
data class PivotPointsResult(
    val pivot: Double,
    val support1: Double,
    val support2: Double,
    val resistance1: Double,
    val resistance2: Double
)

// Zbiorczy wynik wszystkich wskaźników
data class IndicatorResults(
    val macd: MacdResult?,
    val adaptiveMacd: MacdResult?,
    val rsi: List<Double>?,
    val bollingerBands: BollingerBandsResult?,
    val stochastic: StochasticResult?,
    val ichimoku: IchimokuResult?,
    val adx: List<Double>?,
    val atr: List<Double>?,
    val pivotPoints: PivotPointsResult?
)

object Indicators {
    private val logger = LoggerFactory.getLogger(Indicators::class.java)

    private fun computeSma(prices: List<Double>, period: Int): List<Double> {
        if (period <= 0 || prices.size < period) return emptyList()
        return (period until prices.size).map { i ->
            prices.subList(i - period, i).average()
        }
    }

    fun computeEma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty() || period <= 0) return emptyList()
        val k = 2.0 / (period + 1)
        val ema = mutableListOf(prices.first())
        for (i in 1 until prices.size) {
            val currentEma = prices[i] * k + ema.last() * (1 - k)
            ema.add(currentEma)
        }
        return ema
    }

    fun computeMacd(prices: List<Double>, fastPeriod: Int, slowPeriod: Int, signalPeriod: Int): MacdResult {
        val fastEma = computeEma(prices, fastPeriod)
        val slowEma = computeEma(prices, slowPeriod)
        val macdLine = fastEma.zip(slowEma) { f, s -> f - s }
        val signalLine = computeEma(macdLine, signalPeriod)
        val histogram = macdLine.zip(signalLine) { m, s -> m - s }
        return MacdResult(macdLine, signalLine, histogram)
    }

    fun computeAdaptiveMacd(
        prices: List<Double>,
        baseFast: Int,
        baseSlow: Int,
        baseSignal: Int,
        bbPeriod: Int = 20,
        bbNumDevs: Double = 2.0
    ): MacdResult {
        val bb = computeBollingerBands(prices, bbPeriod, bbNumDevs)
        if (bb.middle.isEmpty() || bb.upper.isEmpty()) {
            return computeMacd(prices, baseFast, baseSlow, baseSignal)
        }
        val currentMiddle = bb.middle.last()
        val currentUpper = bb.upper.last()
        val volatilityRatio = (currentUpper - currentMiddle) / currentMiddle

        fun adaptivePeriod(base: Int): Int {
            val scaled = (base * (1 - volatilityRatio)).toInt()
            return scaled.coerceIn(2, base)
        }
        val adaptiveFast = adaptivePeriod(baseFast)
        val adaptiveSlow = adaptivePeriod(baseSlow).coerceAtLeast(adaptiveFast + 1)
        val adaptiveSignal = adaptivePeriod(baseSignal)

        val fastEma = computeEma(prices, adaptiveFast)
        val slowEma = computeEma(prices, adaptiveSlow)
        val macdLine = fastEma.zip(slowEma) { f, s -> f - s }
        val signalLine = computeEma(macdLine, adaptiveSignal)
        val histogram = macdLine.zip(signalLine) { m, s -> m - s }
        return MacdResult(macdLine, signalLine, histogram)
    }

    fun computeRsi(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period + 1) {
            logger.info("computeRsi: insufficient data, prices.size=${prices.size}, period=$period")
            return emptyList()
        }
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) -it else 0.0 }
        var avgGain = gains.subList(0, period).average()
        var avgLoss = losses.subList(0, period).average()
        val rsiList = mutableListOf<Double>()
        val firstRsi = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        rsiList.add(firstRsi)
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

    fun computeAtr(klines: List<Kline>, period: Int): List<Double> {
        if (klines.size < period + 1) return emptyList()
        val trList = mutableListOf<Double>()
        for (i in 1 until klines.size) {
            val high = klines[i].highPrice.toDouble()
            val low = klines[i].lowPrice.toDouble()
            val prevClose = klines[i - 1].closePrice.toDouble()
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList.add(tr)
        }
        val atrList = mutableListOf<Double>()
        var currentAtr = trList.subList(0, period).average()
        atrList.add(currentAtr)
        for (i in period until trList.size) {
            currentAtr = (currentAtr * (period - 1) + trList[i]) / period
            atrList.add(currentAtr)
        }
        return atrList
    }

    fun computeBollingerBands(prices: List<Double>, period: Int, numDevs: Double): BollingerBandsResult {
        if (prices.size < period) return BollingerBandsResult(emptyList(), emptyList(), emptyList())
        val sma = computeSma(prices, period)
        val stdDev = (period until prices.size).map { i ->
            val sublist = prices.subList(i - period, i)
            val mean = sublist.average()
            sqrt(sublist.map { (it - mean) * (it - mean) }.sum() / period)
        }
        val upper = sma.zip(stdDev) { m, s -> m + numDevs * s }
        val lower = sma.zip(stdDev) { m, s -> m - numDevs * s }
        return BollingerBandsResult(sma, upper, lower)
    }

    fun computeStochasticOscillator(klines: List<Kline>, period: Int, dPeriod: Int = 3): StochasticResult {
        val kValues = mutableListOf<Double>()
        for (i in period - 1 until klines.size) {
            val slice = klines.subList(i - period + 1, i + 1)
            val highs = slice.map { it.highPrice.toDouble() }
            val lows = slice.map { it.lowPrice.toDouble() }
            val highestHigh = highs.maxOrNull() ?: continue
            val lowestLow = lows.minOrNull() ?: continue
            val currentClose = klines[i].closePrice.toDouble()
            val k = if (highestHigh - lowestLow == 0.0) 50.0
            else ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100.0
            kValues.add(k)
        }
        val dValues = mutableListOf<Double>()
        for (i in dPeriod - 1 until kValues.size) {
            val d = kValues.subList(i - dPeriod + 1, i + 1).average()
            dValues.add(d)
        }
        return StochasticResult(kValues, dValues)
    }

    fun computeIchimoku(klines: List<Kline>, tenkanPeriod: Int = 9, kijunPeriod: Int = 26, senkouSpanBPeriod: Int = 52, displacement: Int = 26): IchimokuResult {
        if (klines.size < senkouSpanBPeriod) return IchimokuResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val tenkanSen = mutableListOf<Double>()
        val kijunSen = mutableListOf<Double>()
        val senkouSpanA = mutableListOf<Double>()
        val senkouSpanB = mutableListOf<Double>()
        val chikouSpan = mutableListOf<Double>()
        for (i in tenkanPeriod - 1 until klines.size) {
            val slice = klines.subList(i - tenkanPeriod + 1, i + 1)
            val high = slice.maxOf { it.highPrice.toDouble() }
            val low = slice.minOf { it.lowPrice.toDouble() }
            tenkanSen.add((high + low) / 2.0)
        }
        for (i in kijunPeriod - 1 until klines.size) {
            val slice = klines.subList(i - kijunPeriod + 1, i + 1)
            val high = slice.maxOf { it.highPrice.toDouble() }
            val low = slice.minOf { it.lowPrice.toDouble() }
            kijunSen.add((high + low) / 2.0)
        }
        val baseLength = min(tenkanSen.size, kijunSen.size)
        for (i in 0 until baseLength) {
            senkouSpanA.add((tenkanSen[i] + kijunSen[i]) / 2.0)
        }
        for (i in senkouSpanBPeriod - 1 until klines.size) {
            val slice = klines.subList(i - senkouSpanBPeriod + 1, i + 1)
            val high = slice.maxOf { it.highPrice.toDouble() }
            val low = slice.minOf { it.lowPrice.toDouble() }
            senkouSpanB.add((high + low) / 2.0)
        }
        val senkouSpanAForward = List(displacement) { Double.NaN } + senkouSpanA
        val senkouSpanBForward = List(displacement) { Double.NaN } + senkouSpanB
        val closePrices = klines.map { it.closePrice.toDouble() }
        val chikouSpanShifted = if (closePrices.size > displacement)
            closePrices.drop(displacement) + List(displacement) { Double.NaN }
        else List(closePrices.size) { Double.NaN }
        return IchimokuResult(tenkanSen, kijunSen, senkouSpanAForward, senkouSpanBForward, chikouSpanShifted)
    }

    fun computeParabolicSar(klines: List<Kline>, initialAF: Double = 0.02, stepAF: Double = 0.02, maxAF: Double = 0.2): List<Double> {
        if (klines.size < 2) return emptyList()
        val sarValues = mutableListOf<Double>()
        var isUptrend = klines[1].closePrice.toDouble() > klines[0].closePrice.toDouble()
        var sar = if (isUptrend) klines[0].lowPrice.toDouble() else klines[0].highPrice.toDouble()
        var ep = if (isUptrend) klines[1].highPrice.toDouble() else klines[1].lowPrice.toDouble()
        var af = initialAF
        sarValues.add(sar)
        for (i in 1 until klines.size) {
            val currentCandle = klines[i]
            val currentHigh = currentCandle.highPrice.toDouble()
            val currentLow = currentCandle.lowPrice.toDouble()
            sar = sar + af * (ep - sar)
            if (isUptrend) {
                if (currentLow < sar) {
                    isUptrend = false
                    sar = ep
                    ep = currentLow
                    af = initialAF
                } else {
                    if (currentHigh > ep) {
                        ep = currentHigh
                        af = min(af + stepAF, maxAF)
                    }
                    if (i >= 2) {
                        val prevLow = klines[i - 1].lowPrice.toDouble()
                        sar = min(sar, min(prevLow, currentLow))
                    }
                }
            } else {
                if (currentHigh > sar) {
                    isUptrend = true
                    sar = ep
                    ep = currentHigh
                    af = initialAF
                } else {
                    if (currentLow < ep) {
                        ep = currentLow
                        af = min(af + stepAF, maxAF)
                    }
                    if (i >= 2) {
                        val prevHigh = klines[i - 1].highPrice.toDouble()
                        sar = max(sar, max(prevHigh, currentHigh))
                    }
                }
            }
            sarValues.add(sar)
        }
        return sarValues
    }

    fun computeAdx(klines: List<Kline>, period: Int): List<Double> {
        if (klines.size < period + 1) return emptyList()
        val trList = mutableListOf<Double>()
        val plusDMList = mutableListOf<Double>()
        val minusDMList = mutableListOf<Double>()
        for (i in 1 until klines.size) {
            val currentHigh = klines[i].highPrice.toDouble()
            val currentLow = klines[i].lowPrice.toDouble()
            val prevHigh = klines[i - 1].highPrice.toDouble()
            val prevLow = klines[i - 1].lowPrice.toDouble()
            val prevClose = klines[i - 1].closePrice.toDouble()
            val tr = max(currentHigh - currentLow, max(abs(currentHigh - prevClose), abs(currentLow - prevClose)))
            trList.add(tr)
            val plusDM = if ((currentHigh - prevHigh) > (prevLow - currentLow) && (currentHigh - prevHigh) > 0)
                currentHigh - prevHigh else 0.0
            plusDMList.add(plusDM)
            val minusDM = if ((prevLow - currentLow) > (currentHigh - prevHigh) && (prevLow - currentLow) > 0)
                prevLow - currentLow else 0.0
            minusDMList.add(minusDM)
        }
        fun smooth(data: List<Double>): List<Double> {
            if (data.size < period) return emptyList()
            val smoothed = mutableListOf<Double>()
            smoothed.add(data.subList(0, period).average())
            for (i in period until data.size) {
                val prev = smoothed.last()
                val newVal = (prev * (period - 1) + data[i]) / period
                smoothed.add(newVal)
            }
            return smoothed
        }
        val smoothedTR = smooth(trList)
        val smoothedPlusDM = smooth(plusDMList)
        val smoothedMinusDM = smooth(minusDMList)
        val dxList = mutableListOf<Double>()
        for (i in smoothedTR.indices) {
            val trVal = smoothedTR[i]
            if (trVal == 0.0) {
                dxList.add(0.0)
            } else {
                val plusDI = (smoothedPlusDM[i] / trVal) * 100.0
                val minusDI = (smoothedMinusDM[i] / trVal) * 100.0
                val dx = if (plusDI + minusDI == 0.0) 0.0 else (abs(plusDI - minusDI) / (plusDI + minusDI)) * 100.0
                dxList.add(dx)
            }
        }
        if (dxList.size < period) return emptyList()
        val adx = mutableListOf<Double>()
        adx.add(dxList.subList(0, period).average())
        for (i in period until dxList.size) {
            val prevAdx = adx.last()
            val newAdx = (prevAdx * (period - 1) + dxList[i]) / period
            adx.add(newAdx)
        }
        return adx
    }

    fun computePivotPoints(candle: Kline): PivotPointsResult {
        val high = candle.highPrice.toDouble()
        val low = candle.lowPrice.toDouble()
        val close = candle.closePrice.toDouble()
        val pivot = (high + low + close) / 3.0
        val support1 = 2 * pivot - high
        val resistance1 = 2 * pivot - low
        val support2 = pivot - (high - low)
        val resistance2 = pivot + (high - low)
        return PivotPointsResult(pivot, support1, support2, resistance1, resistance2)
    }
}
