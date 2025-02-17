package strategy

import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min

class BreakoutStrategy(
    private val lookback: Int = 20,
    private val riskPercent: Double = 0.02, // 2% kapitału
    private val maxRiskUsd: Double = 100.0,
    private val slPct: Double = 0.01, // 1% SL
    private val rrRatio: Double = 2.0 // RR=2
) : Strategy {

    override val name: String = "BreakoutStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        if (candles.size < lookback + 1) return signals

        // Bierzemy `lookback` poprzednich świec, BEZ aktualnej
        val recent = candles.takeLast(lookback)
        val maxHigh = recent.maxOfOrNull { it.highPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN
        val minLow  = recent.minOfOrNull { it.lowPrice.toDoubleOrNull() ?: Double.NaN } ?: Double.NaN

        val close = candle.closePrice.toDoubleOrNull() ?: return signals
        if (maxHigh.isNaN() || minLow.isNaN()) return signals

        // 1) BUY, jeśli close > maxHigh
        if (close > maxHigh) {
            val riskAmount = min(capital * riskPercent, maxRiskUsd)
            val stopLoss = close * (1.0 - slPct)
            val takeProfit = close + (close - stopLoss) * rrRatio
            val riskPerUnit = close - stopLoss
            if (riskPerUnit <= 0) return signals
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    SignalType.BUY,
                    close,
                    stopLoss,
                    takeProfit,
                    quantity
                )
            )
        }
        // 2) SELL, jeśli close < minLow
        else if (close < minLow) {
            val riskAmount = min(capital * riskPercent, maxRiskUsd)
            val stopLoss = close * (1.0 + slPct)
            val takeProfit = close - (stopLoss - close) * rrRatio
            val riskPerUnit = stopLoss - close
            if (riskPerUnit <= 0) return signals
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    SignalType.SELL,
                    close,
                    stopLoss,
                    takeProfit,
                    quantity
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

        // trailing offset
        val offsetPct = 0.005 // 0.5%
        val offset = price * offsetPct

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
