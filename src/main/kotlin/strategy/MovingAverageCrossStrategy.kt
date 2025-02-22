package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min

/**
 * Strategia oparta na przecięciu średnich kroczących (SMA 7 i 25).
 * Dynamiczne dostosowanie parametrów na podstawie bieżącego wolumenu:
 * - Przy wolumenie ≥ highVolumeThreshold: slPct = 0.008, tpPct = 0.06, trailingOffsetPct = 0.004.
 * - W przeciwnym razie: slPct = 0.012, tpPct = 0.045, trailingOffsetPct = 0.0055.
 * Dodatkowo – sygnał generowany tylko, gdy wolumen przekracza minimalny próg (minVolumeForSignal).
 */
class MovingAverageCrossStrategy(
    private val shortPeriod: Int = 7,
    private val longPeriod: Int = 25,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val highVolumeThreshold: Double = 5000.0,
    private val minVolumeForSignal: Double = 500.0  // dodatkowy próg minimalny
) : Strategy {

    override val name: String = "MovingAverageCrossStrategy"

    override fun onNewCandle(
        candle: Kline,
        candlesSoFar: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closePrices = candlesSoFar.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < longPeriod + 1) return signals

        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        if (currentVolume < minVolumeForSignal) return signals  // brak wystarczającej płynności

        val (slPct, tpPct, trailingOffsetPct) = if (currentVolume >= highVolumeThreshold) {
            Triple(0.008, 0.06, 0.004)
        } else {
            Triple(0.012, 0.045, 0.0055)
        }

        val smaShort = Indicators.computeSma(closePrices, shortPeriod)
        val smaLong = Indicators.computeSma(closePrices, longPeriod)
        val i = closePrices.lastIndex
        if (i < longPeriod) return signals

        val prevDiff = smaShort[i - 1] - smaLong[i - 1]
        val currDiff = smaShort[i] - smaLong[i]
        val price = closePrices[i]
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        if (prevDiff <= 0 && currDiff > 0) {
            // Golden cross – sygnał BUY
            val stopLoss = price * (1.0 - slPct)
            val takeProfit = price * (1.0 + tpPct)
            val riskPerUnit = price - stopLoss
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        } else if (prevDiff >= 0 && currDiff < 0) {
            // Death cross – sygnał SELL
            val stopLoss = price * (1.0 + slPct)
            val takeProfit = price * (1.0 - tpPct)
            val riskPerUnit = stopLoss - price
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        }
        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        val trailingOffsetPct = if (currentVolume >= highVolumeThreshold) 0.004 else 0.0055
        val offset = price * trailingOffsetPct

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop ||
                    (openPosition.takeProfit != null && price >= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price <= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop ||
                    (openPosition.takeProfit != null && price <= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price >= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
        }
        return signals
    }
}
