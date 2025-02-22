package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min

/**
 * Strategia BollingerScalping – dynamiczne ustawienia zależą od bieżącego wolumenu.
 * Przy wolumenie ≥ highVolumeThreshold stosujemy agresywne ustawienia:
 *     slPct = 0.008, tpPct = 0.06, trailingOffsetPct = 0.004.
 * W przeciwnym razie:
 *     slPct = 0.012, tpPct = 0.035, trailingOffsetPct = 0.0055.
 * Dodatkowy filtr: sygnał generowany tylko, gdy wolumen ≥ minVolumeForSignal.
 */
class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val highVolumeThreshold: Double = 5000.0,
    private val minVolumeForSignal: Double = 1000.0  // nowy próg minimalny
) : Strategy {

    override val name: String = "BollingerScalping"

    override fun onNewCandle(
        candle: Kline,
        candlesSoFar: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closeList = candlesSoFar.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < bbPeriod) return signals

        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        // Jeśli wolumen jest za niski, nie generujemy sygnału
        if (currentVolume < minVolumeForSignal) return signals

        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val i = closeList.lastIndex
        if (i < bbPeriod - 1 || i < rsiPeriod - 1) return signals

        val close = closeList[i]
        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val rsi = rsiArr[i]

        val (slPct, tpPct, trailingOffsetPct) = if (currentVolume >= highVolumeThreshold) {
            Triple(0.008, 0.06, 0.004)
        } else {
            Triple(0.012, 0.035, 0.0055)
        }
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        if (close <= lower && rsi < rsiBuyThreshold) {
            // Sygnał BUY
            val stopLoss = close * (1.0 - slPct)
            val takeProfit = close * (1.0 + tpPct)
            val riskPerUnit = close - stopLoss
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = close,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        } else if (close >= upper && rsi > rsiSellThreshold) {
            // Sygnał SELL
            val stopLoss = close * (1.0 + slPct)
            val takeProfit = close * (1.0 - tpPct)
            val riskPerUnit = stopLoss - close
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = close,
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
