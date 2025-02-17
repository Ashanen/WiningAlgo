package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Prosty Mean Reversion:
 * - Liczymy SMA(maPeriod) i ATR(atrPeriod).
 * - Jeśli cena < sma - atr => BUY
 * - Jeśli cena > sma + atr => SELL
 * - Bez trailing stop (lub minimalny).
 */
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

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        val i = closeList.lastIndex
        if (i < maPeriod - 1 || i < atrPeriod) return signals

        // computeSma
        val maArray = Indicators.computeSma(closeList, maPeriod)
        val sma = maArray[i]
        if (sma.isNaN()) return signals

        // computeAtr (z całych candles lub candles.takeLast(atrPeriod*2))
        val atr = Indicators.calculateATR(candles.takeLast(atrPeriod + 1))

        val price = closeList[i]
        if (atr <= 0) return signals

        // Warunek: MeanReversion - BUY gdy cena < sma - 1*ATR
        if (price < (sma - atr)) {
            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = price,
                    stopLoss = price - atr,         // przykładowy stopLoss
                    takeProfit = price + atr * 2.0, // przykładowy TP
                    quantity = computeQuantity(capital, atr, price)
                )
            )
        }
        // SELL gdy cena > sma + 1*ATR
        else if (price > (sma + atr)) {
            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = price,
                    stopLoss = price + atr,
                    takeProfit = price - atr * 2.0,
                    quantity = computeQuantity(capital, atr, price)
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

        // Prosty exit: trailing minimalny
        val atr = Indicators.calculateATR(listOf(candle)).coerceAtLeast(1.0)

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - atr
                if (price <= trailingStop || price >= openPosition.takeProfit || price <= openPosition.stopLoss) {
                    signals.add(
                        StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity)
                    )
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + atr
                if (price >= trailingStop || price <= openPosition.takeProfit || price >= openPosition.stopLoss) {
                    signals.add(
                        StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity)
                    )
                }
            }
        }
        return signals
    }

    private fun computeQuantity(capital: Double, atr: Double, price: Double): Double {
        val riskPercent = 0.02
        val riskUsd = capital * riskPercent
        val riskPerUnit = max(atr, price * 0.01)
        return riskUsd / riskPerUnit
    }
}
