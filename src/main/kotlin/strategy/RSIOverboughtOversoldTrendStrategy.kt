package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal

/**
 * RSI Overbought/Oversold z minimalnym trailing stop
 */
class RSIOverboughtOversoldTrendStrategy(
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < rsiPeriod) return signals

        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiArr.lastOrNull() ?: return signals
        val close = closeList.last()

        if (rsi < rsiBuyThreshold) {
            val stopLoss = close - (close * 0.01)
            val takeProfit = close + (close * 0.02)
            val qty = (capital * 0.02) / (close * 0.01)
            signals.add(
                StrategySignal(
                    SignalType.BUY,
                    close,
                    stopLoss,
                    takeProfit,
                    qty
                )
            )
        } else if (rsi > rsiSellThreshold) {
            val stopLoss = close + (close * 0.01)
            val takeProfit = close - (close * 0.02)
            val qty = (capital * 0.02) / (close * 0.01)
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

        val offset = price * 0.003
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (
                    price <= trailingStop ||
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
                if (
                    price >= trailingStop ||
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
