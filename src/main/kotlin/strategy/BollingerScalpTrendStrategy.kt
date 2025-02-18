package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min

/**
 * Przykładowa strategia Bollinger Scalp + Trend:
 * - Liczymy Bollinger (bbPeriod, bbDev).
 * - BUY, gdy close < lowerBand i np. jest trend UP (lub RSI < X)
 * - SELL, gdy close > upperBand i trend DOWN (lub RSI > Y)
 * - Tu: prosty "trend" to np. sprawdzamy, czy close > middle
 */
class BollingerScalpTrendStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0
) : Strategy {

    override val name: String = "BollingerScalpTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < bbPeriod) return signals

        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val i = closeList.lastIndex
        if (i < bbPeriod - 1) return signals

        val close = closeList[i]
        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val middle = bb.middle[i]

        // Prosty "trend" – sprawdzamy, czy close powyżej middle => UP, poniżej => DOWN
        val isUpTrend = (close > middle)

        // BUY warunek: close < lowerBand && isUpTrend
        if (close < lower && isUpTrend) {
            val stopLoss = close - (close * 0.005)
            val takeProfit = close + (close * 0.01)

            // Przykładowe obliczenie rawQty:
            val rawQty = (capital * 0.02) / (close * 0.005)

            // Klampujemy do 0.002
            val maxQty = 0.002
            val qty = min(rawQty, maxQty)

            signals.add(
                StrategySignal(
                    SignalType.BUY,
                    close,
                    stopLoss,
                    takeProfit,
                    qty
                )
            )
        }
        // SELL warunek: close > upperBand && !isUpTrend
        else if (close > upper && !isUpTrend) {
            val stopLoss = close + (close * 0.005)
            val takeProfit = close - (close * 0.01)

            val rawQty = (capital * 0.02) / (close * 0.005)

            // Klamp do 0.002
            val maxQty = 0.002
            val qty = min(rawQty, maxQty)

            signals.add(
                StrategySignal(
                    SignalType.SELL,
                    close,
                    stopLoss,
                    takeProfit,
                    qty
                )
            )
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // Minimal trailing offset
        val offset = price * 0.003
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop ||
                    price >= openPosition.takeProfit ||
                    price <= openPosition.stopLoss
                ) {
                    signals.add(
                        StrategySignal(
                            SignalType.CLOSE,
                            price,
                            0.0,
                            0.0,
                            openPosition.quantity
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
                    price <= openPosition.takeProfit ||
                    price >= openPosition.stopLoss
                ) {
                    signals.add(
                        StrategySignal(
                            SignalType.CLOSE,
                            price,
                            0.0,
                            0.0,
                            openPosition.quantity
                        )
                    )
                }
            }
        }
        return signals
    }
}
