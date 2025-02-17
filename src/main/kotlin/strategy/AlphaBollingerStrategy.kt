package strategy

import compute.ExtraFilters.averageVolume
import compute.ExtraFilters.isInTradingHours
import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min
import kotlin.math.max

class AlphaBollingerStrategy(
    override val name: String = "AlphaBollingerStrategy"
) : Strategy {

    private val bbPeriod = 20
    private val bbMultiplier = 2.0
    private val lookbackPeriod = 20
    private val riskRewardRatio = 3.0
    private val minRiskPerUnitMultiplier = 0.025
    private val maxRiskUsd = 100.0
    private val riskPercentage = 0.05

    // Nowe parametry filtrów
    private val startHour = 8    // handluj od 08:00 UTC
    private val endHour = 20     // do 20:00 UTC
    private val volPeriod = 20   // z ilu świec liczyć średni wolumen
    private val volMultiplier = 0.8 // aktualny wolumen musi być > 0.8 * avgVol

    override fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        // 1) Filtr godzinowy
        if (!isInTradingHours(candle, startHour, endHour)) {
            return signals
        }

        // 2) Filtr wolumenu
        val avgVol = averageVolume(candles, volPeriod)
        val currentVol = candle.volume.toDoubleOrNull() ?: 0.0
        if (avgVol.isNaN() || currentVol < avgVol * volMultiplier) {
            return signals
        }
        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < bbPeriod) return signals

        // Bollinger
        val bb = Indicators.computeBollingerBands(
            prices = closePrices.takeLast(bbPeriod),
            period = bbPeriod,
            multiplier = bbMultiplier
        )
        val upperBand = bb.upper.last()
        val lowerBand = bb.lower.last()

        val price = candle.closePrice.toDoubleOrNull() ?: return signals
        // RSI
        val rsiList = Indicators.computeRsi(closePrices, 14)
        val rsi = rsiList.lastOrNull() ?: 50.0

        // ATR (z ostatnich lookbackPeriod świec)
        val lastCandles = candles.takeLast(lookbackPeriod)
        val atr = Indicators.calculateATR(lastCandles)

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
                    SignalType.BUY,
                    price,
                    stopLoss,
                    takeProfit,
                    quantity
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
                    SignalType.SELL,
                    price,
                    stopLoss,
                    takeProfit,
                    quantity
                )
            )
        }

        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals
        // ATR z jednej świecy to 0 -> zrobmy minimalny offset
        val atr = Indicators.calculateATR(listOf(candle)).coerceAtLeast(1.0)

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - atr
                if (price <= trailingStop || price >= openPosition.takeProfit || price <= openPosition.stopLoss) {
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
                val trailingStop = openPosition.minFavorable + atr
                if (price >= trailingStop || price <= openPosition.takeProfit || price >= openPosition.stopLoss) {
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
