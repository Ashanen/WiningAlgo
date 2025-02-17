package strategy

import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.max
import kotlin.math.min

/**
 * Prosty Breakout:
 * - Patrzymy wstecz (lookback) => max High, min Low
 * - Jeśli currentHigh > maxHigh => BUY
 * - Jeśli currentLow < minLow => SELL
 */
class BreakoutStrategy(
    private val lookback: Int = 20
) : Strategy {

    override val name: String = "BreakoutStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        if (candles.size < lookback) return signals

        val slice = candles.takeLast(lookback)
        val maxHigh = slice.maxOfOrNull { it.highPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN
        val minLow = slice.minOfOrNull { it.lowPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN

        val currentHigh = candle.highPrice.toDoubleOrNull() ?: return signals
        val currentLow = candle.lowPrice.toDoubleOrNull() ?: return signals
        val close = candle.closePrice.toDoubleOrNull() ?: return signals

        if (currentHigh.isNaN() || currentLow.isNaN()) return signals

        // Prosty warunek
        if (currentHigh > maxHigh) {
            // BUY
            val stopLoss = close - (close * 0.01)
            val takeProfit = close + (close * 0.02)
            val qty = (capital * 0.02) / (close * 0.01) // prosta formuła
            signals.add(StrategySignal(SignalType.BUY, close, stopLoss, takeProfit, qty))
        } else if (currentLow < minLow) {
            // SELL
            val stopLoss = close + (close * 0.01)
            val takeProfit = close - (close * 0.02)
            val qty = (capital * 0.02) / (close * 0.01)
            signals.add(StrategySignal(SignalType.SELL, close, stopLoss, takeProfit, qty))
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // Minimal trailing
        val offset = price * 0.005
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop || price >= openPosition.takeProfit || price <= openPosition.stopLoss) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity))
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop || price <= openPosition.takeProfit || price >= openPosition.stopLoss) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity))
                }
            }
        }
        return signals
    }
}
