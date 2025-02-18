package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.abs
import kotlin.math.min

/**
 * Bollinger + RSI scalp/trend approach with minimal risk-based sizing:
 *  - riskPercent = 2% of capital
 *  - leverage clamp to avoid exceeding capital*leverage
 *  - 1% stop, 2% takeProfit => R:R ~ 1:2
 */
class BollingerScalpTrendStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,

    private val riskPercent: Double = 0.02,   // risk 2% of capital
    private val leverage: Double = 5.0,       // clamp notional to capital*leverage
    private val rrRatio: Double = 2.0         // 1:2 risk:reward
) : Strategy {

    override val name: String = "BollingerScalpTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        if (candles.size < bbPeriod) return signals

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val i = closeList.lastIndex
        if (i < bbPeriod - 1) return signals

        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val close = closeList[i]

        // Also check RSI
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiArr.lastOrNull() ?: 50.0

        // BUY condition => price < lowerBand && RSI < threshold
        if (close <= lower && rsi < rsiBuyThreshold) {
            // e.g. 1% stop => 2% target
            val stopLoss = close * 0.99
            val takeProfit = close * (1.0 + 0.01 * rrRatio)
            if (stopLoss <= 0.0) return signals

            // Risk in USD
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
        // SELL condition => price > upperBand && RSI > threshold
        else if (close >= upper && rsi > rsiSellThreshold) {
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
