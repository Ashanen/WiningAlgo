package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.max
import kotlin.math.min

/**
 * Prosty Bollinger Scalping:
 * - Liczymy Bollinger (bbPeriod, bbDev).
 * - RSI (rsiPeriod).
 * - BUY gdy close < lowerBand && RSI < X
 * - SELL gdy close > upperBand && RSI > Y
 */
class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0
) : Strategy {

    override val name: String = "BollingerScalping"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < bbPeriod) return signals

        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val i = closeList.lastIndex
        if (i < bbPeriod - 1) return signals

        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val close = closeList[i]

        // RSI
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiArr.lastOrNull() ?: 50.0

        if (close <= lower && rsi < rsiBuyThreshold) {
            // BUY
            val stopLoss = close - (close * 0.005)
            val takeProfit = close + (close * 0.01)
            val qty = (capital * 0.02) / (close * 0.005)
            signals.add(StrategySignal(SignalType.BUY, close, stopLoss, takeProfit, qty))
        } else if (close >= upper && rsi > rsiSellThreshold) {
            // SELL
            val stopLoss = close + (close * 0.005)
            val takeProfit = close - (close * 0.01)
            val qty = (capital * 0.02) / (close * 0.005)
            signals.add(StrategySignal(SignalType.SELL, close, stopLoss, takeProfit, qty))
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // Minimal trailing
        val offset = price * 0.003
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop || price >= openPosition.takeProfit || price <= openPosition.stopLoss) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity))
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop || price <= openPosition.takeProfit || price >= openPosition.stopLoss) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, 0.0, 0.0, openPosition.quantity))
                }
            }
        }
        return signals
    }
}
