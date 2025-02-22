package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min

class EnhancedAdaptiveMACDStrategy(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    private val volumeThreshold: Double = 10000.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val slPct: Double = 0.01,
    private val tpPct: Double = 0.02
) : Strategy {

    override val name: String = "EnhancedAdaptiveMACDStrategy"

    override fun onNewCandle(
        candle: Kline,
        candlesSoFar: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closePrices = candlesSoFar.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < slowPeriod) return signals

        val macdResult = Indicators.computeMacd(closePrices, fastPeriod, slowPeriod, signalPeriod)
        val latestIndex = closePrices.lastIndex
        val macdValue = macdResult.macd[latestIndex]
        val signalValue = macdResult.signal[latestIndex]
        val histogram = macdResult.histogram[latestIndex]

        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        if (currentVolume < volumeThreshold) return signals

        val rsiArray = Indicators.computeRsi(closePrices, rsiPeriod)
        val latestRsi = rsiArray[latestIndex]

        val price = closePrices[latestIndex]

        if (macdValue > signalValue && latestRsi < rsiBuyThreshold) {
            val stopLoss = price * (1 - slPct)
            val takeProfit = price * (1 + tpPct)
            val riskPerUnit = price - stopLoss
            if (riskPerUnit > 0) {
                val quantity = min(capital * 0.01 / riskPerUnit, 1.0)
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity
                    )
                )
            }
        } else if (macdValue < signalValue && latestRsi > rsiSellThreshold) {
            val stopLoss = price * (1 + slPct)
            val takeProfit = price * (1 - tpPct)
            val riskPerUnit = stopLoss - price
            if (riskPerUnit > 0) {
                val quantity = min(capital * 0.01 / riskPerUnit, 1.0)
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity
                    )
                )
            }
        }
        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        if (openPosition.side == "BUY") {
            if (price > openPosition.maxFavorable) {
                openPosition.maxFavorable = price
            }
            val trailingStop = openPosition.maxFavorable * (1 - slPct)
            if (price <= trailingStop || (openPosition.takeProfit != null && price >= openPosition.takeProfit)) {
                signals.add(
                    StrategySignal(
                        type = SignalType.CLOSE,
                        price = price,
                        stopLoss = 0.0,
                        takeProfit = 0.0,
                        quantity = openPosition.quantity
                    )
                )
            }
        } else if (openPosition.side == "SELL") {
            if (price < openPosition.minFavorable) {
                openPosition.minFavorable = price
            }
            val trailingStop = openPosition.minFavorable * (1 + slPct)
            if (price >= trailingStop || (openPosition.takeProfit != null && price <= openPosition.takeProfit)) {
                signals.add(
                    StrategySignal(
                        type = SignalType.CLOSE,
                        price = price,
                        stopLoss = 0.0,
                        takeProfit = 0.0,
                        quantity = openPosition.quantity
                    )
                )
            }
        }
        return signals
    }
}
