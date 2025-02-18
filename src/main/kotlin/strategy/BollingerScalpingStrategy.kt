package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min
import kotlin.math.max

class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val riskPercent: Double = 0.02,   // 2% kapitału
    private val maxRiskUsd: Double = 100.0,   // max 100 USD na trade
    private val slPct: Double = 0.015,        // 1.5% stop-loss
    private val tpPct: Double = 0.03,         // 3.0% take-profit
    private val trailingOffsetPct: Double = 0.008 // 0.8% trailing offset
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
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)

        val i = closeList.lastIndex
        if (i < bbPeriod - 1 || i < rsiPeriod - 1) return signals

        val close = closeList[i]
        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val rsi = rsiArr[i]

        // Ryzyko
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // 1) Sygnał BUY
        if (close <= lower && rsi < rsiBuyThreshold) {
            val stopLoss = close * (1.0 - slPct)      // np. 1.5% poniżej ceny
            val takeProfit = close * (1.0 + tpPct)    // np. 3.0% powyżej ceny
            val riskPerUnit = close - stopLoss
            if (riskPerUnit <= 0.0) return signals
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = quantity
                )
            )
        }
        // 2) Sygnał SELL
        else if (close >= upper && rsi > rsiSellThreshold) {
            val stopLoss = close * (1.0 + slPct)
            val takeProfit = close * (1.0 - tpPct)
            val riskPerUnit = stopLoss - close
            if (riskPerUnit <= 0.0) return signals
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = quantity
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

        // trailing offset
        val offset = price * trailingOffsetPct

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
