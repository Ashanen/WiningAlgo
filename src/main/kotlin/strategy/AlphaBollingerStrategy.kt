package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.max
import kotlin.math.min

class AlphaBollingerStrategy(
    override val name: String = "AlphaBollingerStrategy"
) : Strategy {

    // Zmodyfikowane parametry
    private val bbPeriod = 24               // np. 24 zamiast 20
    private val bbMultiplier = 2.2          // np. 2.2 zamiast 2.0
    private val lookbackPeriod = 20
    private val riskRewardRatio = 2.5       // zmniejszamy z 3.0 do 2.5
    private val minRiskPerUnitMultiplier = 0.03
    private val maxRiskUsd = 150.0
    private val riskPercentage = 0.04       // nieco mniejszy/mniejszy od 0.05

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < bbPeriod) return signals

        // 1) Oblicz Bollinger
        val bb = Indicators.computeBollingerBands(
            prices = closePrices.takeLast(bbPeriod),
            period = bbPeriod,
            multiplier = bbMultiplier
        )
        val upperBand = bb.upper.last()
        val lowerBand = bb.lower.last()

        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // 2) RSI (opcjonalnie) – np. weryfikujemy warunek rsi > 50 / < 50
        val rsiList = Indicators.computeRsi(closePrices, 14)
        val rsi = rsiList.lastOrNull() ?: 50.0

        // 3) ATR do wyliczenia ryzyka
        val recentCandles = candles.takeLast(lookbackPeriod)
        val atr = Indicators.calculateATR(recentCandles)

        // Warunek BUY
        if (price > upperBand && rsi > 50) {
            val riskAmount = min(capital * riskPercentage, maxRiskUsd)
            val riskPerUnit = max(atr, price * minRiskPerUnitMultiplier)
            if (riskPerUnit <= 0) return signals

            val stopLoss = price - riskPerUnit
            val takeProfit = price + riskPerUnit * riskRewardRatio
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = price,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = quantity
                )
            )
        }
        // Warunek SELL
        else if (price < lowerBand && rsi < 50) {
            val riskAmount = min(capital * riskPercentage, maxRiskUsd)
            val riskPerUnit = max(atr, price * minRiskPerUnitMultiplier)
            if (riskPerUnit <= 0) return signals

            val stopLoss = price + riskPerUnit
            val takeProfit = price - riskPerUnit * riskRewardRatio
            val quantity = riskAmount / riskPerUnit

            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = price,
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

        // Przykład: trailing stop oparty na ATR
        // Zamiast brać ATR z 1 świecy, można brać np. candles.takeLast(N).
        // Dodatkowo można mnożyć ATR x 1.5, by trailingStop był szerszy.

        val atr =  Indicators.calculateATR(listOf(candle)) // lub candles.takeLast(10)
        val multiplier = 1.5                               // mocniejszy trailing
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - (atr * multiplier)
                if (price <= trailingStop
                    || price >= openPosition.takeProfit
                    || price <= openPosition.stopLoss
                ) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            stopLoss = 0.0,
                            takeProfit = 0.0,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + (atr * multiplier)
                if (price >= trailingStop
                    || price <= openPosition.takeProfit
                    || price >= openPosition.stopLoss
                ) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            stopLoss = 0.0,
                            takeProfit = 0.0,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
        }

        return signals
    }
}
