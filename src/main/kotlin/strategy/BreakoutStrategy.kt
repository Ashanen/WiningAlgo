package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min
import kotlin.math.max

class BreakoutStrategy(
    private val lookback: Int = 20,
    private val atrPeriod: Int = 14,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 150.0,  // zwiększamy z 100
    private val rrRatio: Double = 2.0        // R:R = 2:1
) : Strategy {

    override val name: String = "BreakoutStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        if (candles.size < lookback + atrPeriod) return signals

        val close = candle.closePrice.toDoubleOrNull() ?: return signals
        val slice = candles.takeLast(lookback)

        val maxHigh = slice.maxOfOrNull { it.highPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN
        val minLow  = slice.minOfOrNull { it.lowPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN

        // ATR z np. ostatnich (lookback+atrPeriod) świec
        val atr = Indicators.calculateATR(candles.takeLast(lookback + atrPeriod))
        if (atr <= 0.0) return signals

        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // Warunek BUY: cena > 1.001 * maxHigh
        if (close > maxHigh * 1.001) {
            val stopLoss = close - (1.0 * atr)
            val takeProfit = close + (1.0 * atr * rrRatio)
            val riskPerUnit = close - stopLoss
            if (riskPerUnit <= 0.0) return signals
            val qty = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = qty
                )
            )
        }
        // Warunek SELL: cena < 0.999 * minLow
        else if (close < minLow * 0.999) {
            val stopLoss = close + (1.0 * atr)
            val takeProfit = close - (1.0 * atr * rrRatio)
            val riskPerUnit = stopLoss - close
            if (riskPerUnit <= 0.0) return signals
            val qty = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = qty
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

        // trailing offset (opcjonalnie):
        // val trailingFactor = 1.5
        // val atr = Indicators.calculateATR(...)

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                // Warunek zamknięcia
                if (price <= openPosition.stopLoss ||
                    price >= openPosition.takeProfit
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
                if (price >= openPosition.stopLoss ||
                    price <= openPosition.takeProfit
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
