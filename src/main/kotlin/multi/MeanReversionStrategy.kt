package multi

import compute.Indicators.computeAtr
import compute.Indicators.computeMaAngle
import compute.Indicators.computeSma
import convert.toCloseDouble
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import strategy.Strategy
import kotlin.math.abs

class MeanReversionStrategy(
    private val maPeriod: Int = 50,
    private val atrPeriod: Int = 14,
    private val angleThreshold: Double = 0.5
) : Strategy {

    override val name: String = "MeanReversion"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        val closeList = candles.toCloseDouble()
        val i = closeList.lastIndex
        if (i < maPeriod - 1 || i < atrPeriod) return signals

        val ma = computeSma(closeList, maPeriod)
        val angleList = computeMaAngle(ma, point = 0.1)
        val atrList = computeAtr(candles, atrPeriod)

        val price = closeList[i]
        val maVal = ma[i]
        val angle = angleList[i]
        val atr = atrList[i]

        if (maVal.isNaN() || atr.isNaN() || angle.isNaN()) return signals

        // Filtr kąta
        if (abs(angle) > angleThreshold) return signals

        val diff = price - maVal
        if (abs(diff) > atr) {
            if (diff < 0) {
                // Cena sporo poniżej MA => spodziewamy się ruchu w górę => BUY
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = price,
                        stopLoss = 0.0,
                        takeProfit = 0.0,
                        quantity = 1.0
                    )
                )
            } else {
                // Cena sporo powyżej MA => SELL
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = price,
                        stopLoss = 0.0,
                        takeProfit = 0.0,
                        quantity = 1.0
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
        // brak trailing/exit
        return emptyList()
    }
}
