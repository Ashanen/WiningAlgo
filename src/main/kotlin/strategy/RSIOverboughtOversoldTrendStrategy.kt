package strategy

import config.StrategyParameters
import model.Kline
import model.StrategySignal
import model.SignalType
import compute.Indicators

/**
 * Strategia oparta na wskaźniku RSI.
 * Generuje sygnał BUY, gdy RSI jest poniżej rsiOversold,
 * a sygnał SELL, gdy RSI jest powyżej rsiOverbought.
 * Poziomy stop loss i take profit ustalane są dynamicznie przy użyciu ATR,
 * a multiplikatory są konfigurowalne przez StrategyParameters.
 */
class RSIOverboughtOversoldTrendStrategy(
    override val name: String = "RSIOverboughtOversoldTrendStrategy"
) : Strategy {
    override fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal> {
        if (candles.isEmpty()) return emptyList()
        val closePrices = candles.map { it.closePrice.toDouble() }
        val currentPrice = closePrices.last()
        val rsiValues = Indicators.computeRsi(closePrices, period = 14)
        if (rsiValues.isEmpty()) return emptyList()
        val currentRSI = rsiValues.last()

        val buyCondition = currentRSI < StrategyParameters.rsiOversold
        val sellCondition = currentRSI > StrategyParameters.rsiOverbought

        // Obliczenie ATR dla dynamicznego ustalania poziomów
        val atrValues = if (StrategyParameters.useATR) Indicators.computeAtr(candles, StrategyParameters.atrPeriod) else emptyList()
        val currentATR = if (atrValues.isNotEmpty()) atrValues.last() else 0.0

        return when {
            buyCondition -> {
                val quantity = computeQuantity(capital, currentPrice)
                listOf(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = currentPrice,
                        stopLoss = if (currentATR > 0)
                            currentPrice - StrategyParameters.atrMultiplierSL_RSI * currentATR
                        else
                            currentPrice * (1 - StrategyParameters.stopLossPercent),
                        takeProfit = if (currentATR > 0)
                            currentPrice + StrategyParameters.atrMultiplierTP_RSI * currentATR
                        else
                            currentPrice * (1 + StrategyParameters.takeProfitPercent),
                        quantity = quantity
                    )
                )
            }
            sellCondition -> {
                val quantity = computeQuantity(capital, currentPrice)
                listOf(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = currentPrice,
                        stopLoss = if (currentATR > 0)
                            currentPrice + StrategyParameters.atrMultiplierSL_RSI * currentATR
                        else
                            currentPrice * (1 + StrategyParameters.stopLossPercent),
                        takeProfit = if (currentATR > 0)
                            currentPrice - StrategyParameters.atrMultiplierTP_RSI * currentATR
                        else
                            currentPrice * (1 - StrategyParameters.takeProfitPercent),
                        quantity = quantity
                    )
                )
            }
            else -> emptyList()
        }
    }

    override fun onUpdatePosition(candle: Kline, candles: List<Kline>, position: model.OpenPosition): List<StrategySignal> {
        // Implementacja aktualizacji pozycji – np. trailing stop
        return emptyList()
    }

    private fun computeQuantity(capital: Double, price: Double): Double {
        val riskPerTrade = capital * StrategyParameters.riskPerTrade
        return riskPerTrade / price
    }
}
