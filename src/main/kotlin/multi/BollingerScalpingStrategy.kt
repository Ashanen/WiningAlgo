package multi

import compute.computeBollingerBands
import compute.computeRsi
import convert.toCloseDouble
import model.Kline
import model.Signal
import model.SignalType
import trading.TradingStrategy

class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0
) : TradingStrategy {

    override val name: String = "BollingerScalping"

    override fun generateSignals(klines: List<Kline>): List<Signal> {
        val signals = mutableListOf<Signal>()

        // 1) Przygotuj listę close
        val closeList = klines.toCloseDouble()

        // 2) Oblicz Bollinger i RSI
        val bb = computeBollingerBands(closeList, bbPeriod, bbDev)
        val rsiList = computeRsi(closeList, rsiPeriod)

        // 3) Iteracja po świecach
        for (i in closeList.indices) {
            if (i < bbPeriod - 1 || i < rsiPeriod) continue

            val close = closeList[i]
            val lower = bb.lower[i]
            val upper = bb.upper[i]
            val rsi = rsiList[i]

            // Warunki
            if (!lower.isNaN() && close <= lower && rsi < rsiBuyThreshold) {
                signals.add(Signal(i, SignalType.BUY, "BB lower & RSI<$rsiBuyThreshold"))
            } else if (!upper.isNaN() && close >= upper && rsi > rsiSellThreshold) {
                signals.add(Signal(i, SignalType.SELL, "BB upper & RSI>$rsiSellThreshold"))
            }
        }

        return signals
    }
}
