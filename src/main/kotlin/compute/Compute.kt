package compute

import model.BollingerBands
import model.Kline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

fun computeBollingerBands(
    closePrices: List<Double>,
    period: Int = 20,
    multiplier: Double = 2.0
): BollingerBands {
    val middle = mutableListOf<Double>()
    val upper = mutableListOf<Double>()
    val lower = mutableListOf<Double>()

    for (i in closePrices.indices) {
        if (i < period - 1) {
            // Za mało danych do obliczenia
            middle.add(Double.NaN)
            upper.add(Double.NaN)
            lower.add(Double.NaN)
        } else {
            val window = closePrices.subList(i - period + 1, i + 1)
            val avg = window.average()
            val variance = window.fold(0.0) { acc, x -> acc + (x - avg) * (x - avg) } / period
            val stdDev = sqrt(variance)

            middle.add(avg)
            upper.add(avg + multiplier * stdDev)
            lower.add(avg - multiplier * stdDev)
        }
    }

    return BollingerBands(middle, upper, lower)
}

fun computeRsi(
    closePrices: List<Double>,
    period: Int = 14
): List<Double> {
    val rsiList = MutableList(closePrices.size) { Double.NaN }
    if (closePrices.size < period) return rsiList

    // Oblicz zmiany
    val changes = closePrices.zipWithNext { prev, curr -> curr - prev }

    // Można liczyć prosto w pętli:
    var gainSum = 0.0
    var lossSum = 0.0

    // Pierwszy "okres" (od 0 do period-2)
    changes.take(period - 1).forEach {
        if (it > 0) gainSum += it else lossSum -= it
    }
    var avgGain = gainSum / period
    var avgLoss = lossSum / period

    // RSI dla index = period-1
    val firstRsi = if (avgLoss == 0.0) 100.0 else {
        val rs = avgGain / avgLoss
        100.0 - (100.0 / (1.0 + rs))
    }
    rsiList[period - 1] = firstRsi

    // Dalej
    for (i in period until closePrices.size) {
        val change = changes[i - 1]
        val gain = if (change > 0) change else 0.0
        val loss = if (change < 0) -change else 0.0

        avgGain = (avgGain * (period - 1) + gain) / period
        avgLoss = (avgLoss * (period - 1) + loss) / period

        val rs = if (avgLoss == 0.0) Double.POSITIVE_INFINITY else avgGain / avgLoss
        val rsi = 100.0 - (100.0 / (1.0 + rs))
        rsiList[i] = rsi
    }

    return rsiList
}

fun computeSma(values: List<Double>, period: Int): List<Double> {
    val result = MutableList(values.size) { Double.NaN }
    for (i in values.indices) {
        if (i < period - 1) continue
        val window = values.subList(i - period + 1, i + 1)
        val avg = window.average()
        result[i] = avg
    }
    return result
}

/**
 * Kąt: tangens = (Y1 - Y2) / X
 * X = 40 * point (zależy od pary)
 */
fun computeMaAngle(ma: List<Double>, point: Double = 0.0001): List<Double> {
    val angleList = MutableList(ma.size) { Double.NaN }
    for (i in 1 until ma.size) {
        if (ma[i].isNaN() || ma[i-1].isNaN()) continue
        val tangens = (ma[i] - ma[i-1]) / (40.0 * point)
        angleList[i] = Math.toDegrees(tangens) // lub zostaw w radianach
    }
    return angleList
}

/**
 * Oblicza ATR (Average True Range) dla listy klines.
 *
 * @param klines lista świec (zawierających high, low, close).
 * @param period okres ATR (domyślnie 14).
 * @return List<Double> o tej samej wielkości co klines, gdzie:
 *         - index < 1 -> ATR nieobliczalny, bo nie ma poprzedniej świecy => Double.NaN
 *         - index < period => w wersji uproszczonej też Double.NaN (lub partial)
 *         - index >= period-1 => wartości ATR
 */
fun computeAtr(klines: List<Kline>, period: Int = 14): List<Double> {
    val size = klines.size
    val atr = MutableList(size) { Double.NaN }
    if (size < 2) return atr  // nie da się liczyć TR

    // Konwersje na Double
    val highs = klines.map { it.highPrice.toDouble() }
    val lows  = klines.map { it.lowPrice.toDouble() }
    val closes= klines.map { it.closePrice.toDouble() }

    // 1) Najpierw obliczamy TR (True Range) dla każdej świecy
    val trList = MutableList(size) { Double.NaN }

    // TR(0) to high(0) - low(0), bo nie mamy poprzedniej świecy
    trList[0] = highs[0] - lows[0]

    // Dla i >= 1
    for (i in 1 until size) {
        val range1 = highs[i] - lows[i]
        val range2 = abs(highs[i] - closes[i - 1])
        val range3 = abs(lows[i] - closes[i - 1])
        val trueRange = max(range1, max(range2, range3))
        trList[i] = trueRange
    }

    // 2) Obliczamy ATR jako EMA z TR
    // Dla uproszczenia:
    //  - index < period -> atr[index] = NaN
    //  - index = period-1 -> średnia z TR(0..period-1)
    //  - index > period-1 -> (poprzedni_atr*(period-1) + TR[i]) / period

    if (size < period) {
        // Mamy zbyt mało świec, więc nic
        return atr
    }

    // Obliczamy sumę TR(0..period-1)
    var sumTR = 0.0
    for (i in 0 until period) {
        sumTR += trList[i]
    }
    val firstAtr = sumTR / period
    atr[period - 1] = firstAtr

    // Teraz liczymy kolejne
    for (i in period until size) {
        val prevAtr = atr[i - 1]
        // (prevAtr*(period-1) + TR[i]) / period
        val newAtr = ((prevAtr * (period - 1)) + trList[i]) / period
        atr[i] = newAtr
    }

    return atr
}



