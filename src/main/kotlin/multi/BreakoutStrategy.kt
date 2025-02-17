package multi

import convert.toCloseDouble
import convert.toHighDouble
import convert.toLowDouble
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import strategy.Strategy

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

        val closeList = candles.toCloseDouble()
        val highList = candles.toHighDouble()
        val lowList  = candles.toLowDouble()

        val i = closeList.lastIndex
        if (i < lookback) return signals

        // Okno lookback
        val windowHigh = highList.subList(i - lookback, i).maxOrNull() ?: Double.NaN
        val windowLow  = lowList.subList(i - lookback, i).minOrNull() ?: Double.NaN

        val currentHigh = highList[i]
        val currentLow  = lowList[i]
        val currentClose = closeList[i]

        // BUY - przebicie High
        if (currentHigh > windowHigh) {
            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = currentClose,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    quantity = 1.0
                )
            )
        }
        // SELL - przebicie Low
        else if (currentLow < windowLow) {
            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = currentClose,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    quantity = 1.0
                )
            )
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        // brak trailing stop
        return emptyList()
    }
}
