package multi

import convert.toCloseDouble
import convert.toHighDouble
import convert.toLowDouble
import model.Kline
import model.Signal
import model.SignalType
import trading.TradingStrategy

class BreakoutStrategy(
    private val lookback: Int = 20
) : TradingStrategy {

    override val name: String = "BreakoutStrategy"

    override fun generateSignals(klines: List<Kline>): List<Signal> {
        val signals = mutableListOf<Signal>()
        val closeList = klines.toCloseDouble()
        val highList = klines.toHighDouble()
        val lowList  = klines.toLowDouble()

        for (i in closeList.indices) {
            if (i < lookback) continue

            val windowHigh = highList.subList(i - lookback, i).maxOrNull() ?: Double.NaN
            val windowLow  = lowList.subList(i - lookback, i).minOrNull() ?: Double.NaN
            val currentHigh = highList[i]
            val currentLow  = lowList[i]

            // Prosty warunek (np. sprawdzamy high danej świecy)
            if (currentHigh > windowHigh) {
                signals.add(Signal(i, SignalType.BUY, "Breakout above last $lookback highs"))
            } else if (currentLow < windowLow) {
                signals.add(Signal(i, SignalType.SELL, "Breakout below last $lookback lows"))
            }
        }
        return signals
    }
}
