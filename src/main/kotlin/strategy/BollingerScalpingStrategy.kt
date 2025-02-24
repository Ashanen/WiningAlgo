package strategy

import config.StrategyParameters
import model.Kline
import model.StrategySignal
import model.SignalType
import compute.Indicators

/**
 * Strategia scalpingowa oparta na Bollinger Bands.
 * Sygnał kupna generowany jest, gdy cena spada poniżej dolnej wstęgi,
 * a sygnał sprzedaży – gdy cena przekracza górną wstęgę.
 * Poziomy stop loss i take profit ustalane są dynamicznie z wykorzystaniem ATR.
 */
class BollingerScalpingStrategy(
    override val name: String = "BollingerScalpingStrategy"
) : Strategy {
    override fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal> {
        if (candles.isEmpty()) return emptyList()
        val closePrices = candles.map { it.closePrice.toDouble() }
        val currentPrice = closePrices.last()
        val bbResult = Indicators.computeBollingerBands(closePrices, StrategyParameters.bbPeriod, StrategyParameters.bbMultiplier)
        if (bbResult.middle.isEmpty() || bbResult.upper.isEmpty() || bbResult.lower.isEmpty()) return emptyList()
        val lowerBand = bbResult.lower.last()
        val upperBand = bbResult.upper.last()

        // Sygnały na podstawie pozycji ceny względem wstęg Bollingera
        val buyCondition = currentPrice < lowerBand
        val sellCondition = currentPrice > upperBand

        // Obliczenie ATR dla dynamicznego ustawienia poziomów
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
                            currentPrice - StrategyParameters.atrMultiplierSL_BB * currentATR
                        else
                            currentPrice * (1 - StrategyParameters.stopLossPercent),
                        takeProfit = if (currentATR > 0)
                            currentPrice + StrategyParameters.atrMultiplierTP_BB * currentATR
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
                            currentPrice + StrategyParameters.atrMultiplierSL_BB * currentATR
                        else
                            currentPrice * (1 + StrategyParameters.stopLossPercent),
                        takeProfit = if (currentATR > 0)
                            currentPrice - StrategyParameters.atrMultiplierTP_BB * currentATR
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
        // Implementacja trailing stop lub innej logiki aktualizacji pozycji
        return emptyList()
    }

    private fun computeQuantity(capital: Double, price: Double): Double {
        val riskPerTrade = capital * StrategyParameters.riskPerTrade
        return riskPerTrade / price
    }
}
