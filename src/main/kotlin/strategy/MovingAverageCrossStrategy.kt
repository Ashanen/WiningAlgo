package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min

/**
 * Strategia otwierająca pozycję na przecięciu średnich kroczących:
 * - Golden cross (SMA7 przecina SMA25 od dołu) generuje sygnał BUY.
 * - Death cross (SMA7 przecina SMA25 od góry) generuje sygnał SELL.
 */
class MovingAverageCrossStrategy(
    private val shortPeriod: Int = 7,
    private val longPeriod: Int = 25,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val slPct: Double = 0.015,
    private val tpPct: Double = 0.03,
    private val trailingOffsetPct: Double = 0.008
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

        // Obliczamy SMA dla krótkiego i długiego okresu
        val smaShort = Indicators.computeSma(closePrices, shortPeriod)
        val smaLong = Indicators.computeSma(closePrices, longPeriod)

        val i = closePrices.lastIndex
        if (i < longPeriod) return signals

        // Sprawdzamy przecięcie SMA: porównujemy różnicę w poprzednim kroku i obecnym
        val prevDiff = smaShort[i - 1] - smaLong[i - 1]
        val currDiff = smaShort[i] - smaLong[i]
        val price = closePrices[i]
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // Golden cross: otwieramy BUY, gdy różnica przechodzi z ujemnej na dodatnią
        if (prevDiff <= 0 && currDiff > 0) {
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
                        quantity = quantity
                    )
                )
            }
        }
        // Death cross: otwieramy SELL, gdy różnica przechodzi z dodatniej na ujemną
        else if (prevDiff >= 0 && currDiff < 0) {
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
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop ||
                    (openPosition.takeProfit != null && price <= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price >= openPosition.stopLoss)
                ) {
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
        }
        return signals
    }
}
