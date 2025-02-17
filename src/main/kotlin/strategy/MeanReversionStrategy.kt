package strategy

import compute.Indicators
import convert.toCloseDouble
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MeanReversionStrategy(
    private val maPeriod: Int = 50,
    private val atrPeriod: Int = 14,
    private val angleThreshold: Double = 1.0, // w stopniach, powiększony
    private val atrFactor: Double = 1.0,      // ile ATR od MA, by zagrać
    private val riskPercent: Double = 0.02,   // 2% kapitału
    private val maxRiskUsd: Double = 100.0,
    private val rrRatio: Double = 2.0         // 2:1
) : Strategy {

    override val name: String = "MeanReversion"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        if (candles.size < maPeriod || candles.size < atrPeriod) return signals

        val closeList = candles.toCloseDouble()
        val i = closeList.lastIndex
        if (i < maPeriod - 1 || i < atrPeriod) return signals

        // 1) Obliczmy SMA
        val maArr = Indicators.computeSma(closeList, maPeriod)
        val maVal = maArr[i]
        if (maVal.isNaN()) return signals

        // 2) Obliczmy kąt nachylenia (dla uproszczenia)
        val angleArr = Indicators.computeMaAngle(maArr, point = 0.1) // np. 0.1 dla BTC
        val angle = angleArr[i]
        if (angle.isNaN() || abs(angle) > angleThreshold) {
            // Jeśli MA ma zbyt duży kąt, nie handlujemy
            return signals
        }

        // 3) ATR
        val atrArr = Indicators.computeAtr(candles, atrPeriod)
        val atrVal = atrArr[i]
        if (atrVal.isNaN() || atrVal <= 0.0) return signals

        val price = closeList[i]
        val diff = price - maVal
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // Warunek: jeśli cena < MA - (atrFactor * ATR) => BUY
        if (price < maVal - (atrVal * atrFactor)) {
            val stopLoss = price - atrVal     // 1x ATR
            val takeProfit = price + (atrVal * rrRatio) // 2:1
            val riskPerUnit = price - stopLoss
            if (riskPerUnit <= 0) return signals
            val qty = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    SignalType.BUY,
                    price,
                    stopLoss,
                    takeProfit,
                    qty
                )
            )
        }
        // Warunek: jeśli cena > MA + (atrFactor * ATR) => SELL
        else if (price > maVal + (atrVal * atrFactor)) {
            val stopLoss = price + atrVal
            val takeProfit = price - (atrVal * rrRatio)
            val riskPerUnit = stopLoss - price
            if (riskPerUnit <= 0) return signals
            val qty = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    SignalType.SELL,
                    price,
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

        // trailing offset, np. 0.5 ATR (opcjonalnie)
        // lub proste sprawdzenie SL/TP
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                // trailing
                val offset = (openPosition.maxFavorable - openPosition.entryPrice) * 0.5
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
                val offset = (openPosition.entryPrice - openPosition.minFavorable) * 0.5
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
