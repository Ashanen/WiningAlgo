package multi

import compute.computeAtr
import compute.computeMaAngle
import compute.computeSma
import convert.toCloseDouble
import model.Kline
import model.Signal
import model.SignalType
import trading.TradingStrategy

class MeanReversionStrategy(
    private val maPeriod: Int = 50,
    private val atrPeriod: Int = 14,
    private val angleThreshold: Double = 0.5  // w stopniach
) : TradingStrategy {

    override val name: String = "MeanReversion"

    override fun generateSignals(klines: List<Kline>): List<Signal> {
        val signals = mutableListOf<Signal>()
        val closeList = klines.toCloseDouble()

        // 1) Oblicz MA
        val ma = computeSma(closeList, maPeriod)

        // 2) Oblicz kąt MA
        val angleList = computeMaAngle(ma, point = 0.1)  // np. 0.1, dostosuj do BTC

        // 3) Oblicz ATR (tu musisz mieć computeAtr, np. period=14)
        val atrList = computeAtr(klines, atrPeriod) // musisz napisać analogicznie

        for (i in closeList.indices) {
            if (i < maPeriod - 1 || i < atrPeriod) continue
            val price = closeList[i]
            val maVal = ma[i]
            val angle = angleList[i]
            val atr = atrList[i]

            if (maVal.isNaN() || atr.isNaN() || angle.isNaN()) continue

            // Filtr kąta: jeżeli |angle| > angleThreshold => skip
            if (kotlin.math.abs(angle) > angleThreshold) continue

            // Sprawdź odchylenie
            val diff = price - maVal
            if (kotlin.math.abs(diff) > atr) {
                if (diff < 0) {
                    // Cena poniżej MA o > 1*ATR => spodziewamy się ruchu w górę => BUY
                    signals.add(Signal(i, SignalType.BUY, "MeanReversion below MA by > ATR"))
                } else {
                    // Cena powyżej MA => SELL
                    signals.add(Signal(i, SignalType.SELL, "MeanReversion above MA by > ATR"))
                }
            }
        }
        return signals
    }
}
