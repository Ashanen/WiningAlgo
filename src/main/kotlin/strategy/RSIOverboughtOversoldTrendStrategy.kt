package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.abs
import kotlin.math.min

/**
 * RSI Overbought/Oversold with minimal risk-based sizing + leverage clamp.
 */
class RSIOverboughtOversoldTrendStrategy(
    private val rsiPeriod: Int = 14,
    private val buyThreshold: Double = 30.0,
    private val sellThreshold: Double = 70.0,

    private val riskPercent: Double = 0.02, // 2% of capital
    private val leverage: Double = 5.0,     // clamp notional
    private val rrRatio: Double = 2.0       // 1:2 risk:reward
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        if (candles.size < rsiPeriod) return signals
        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiArr.lastOrNull() ?: return signals
        val close = closeList.last()

        // RSI < buyThreshold => BUY
        if (rsi < buyThreshold) {
            val stopLoss = close * 0.99
            val takeProfit = close * (1.0 + 0.01 * rrRatio)

            val riskUsd = capital * riskPercent
            val riskPerCoin = abs(close - stopLoss)
            val rawQty = riskUsd / riskPerCoin
            val maxQtyByLeverage = (capital * leverage) / close
            val finalQty = min(rawQty, maxQtyByLeverage)

            if (finalQty > 0.0) {
                signals.add(
                    StrategySignal(
                        SignalType.BUY,
                        close,
                        stopLoss,
                        takeProfit,
                        finalQty
                    )
                )
            }
        }
        // RSI > sellThreshold => SELL
        else if (rsi > sellThreshold) {
            val stopLoss = close * 1.01
            val takeProfit = close * (1.0 - 0.01 * rrRatio)

            val riskUsd = capital * riskPercent
            val riskPerCoin = abs(stopLoss - close)
            val rawQty = riskUsd / riskPerCoin
            val maxQtyByLeverage = (capital * leverage) / close
            val finalQty = min(rawQty, maxQtyByLeverage)

            if (finalQty > 0.0) {
                signals.add(
                    StrategySignal(
                        SignalType.SELL,
                        close,
                        stopLoss,
                        takeProfit,
                        finalQty
                    )
                )
            }
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // minimal trailing approach
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
