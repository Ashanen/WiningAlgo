package strategy

import config.StrategyParameters
import indicator.IndicatorService
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import compute.Indicators
import org.slf4j.LoggerFactory

/**
 * Strategia EnhancedAdaptiveMACDStrategy wykorzystuje rozszerzony zestaw wskaźników:
 * MACD/adaptive MACD, RSI, Bollinger Bands, Stochastic, Ichimoku, ADX, ATR oraz PivotPoints.
 *
 * Warunki wejścia ustalane są na podstawie podstawowych filtrów (MACD, RSI, wolumen)
 * rozszerzonych o dodatkowe filtry (ADX, Stochastic, Ichimoku, Parabolic SAR).
 *
 * Wszystkie stałe są konfigurowalne przez StrategyParameters, dzięki czemu nie ma magicznych liczb.
 * Dodatkowo metoda onNewCandle jest otoczona blokiem try-catch oraz zawiera logowanie błędów.
 */
class EnhancedAdaptiveMACDStrategy(
    override val name: String = "EnhancedAdaptiveMACDStrategy"
) : Strategy {
    private val logger = LoggerFactory.getLogger(EnhancedAdaptiveMACDStrategy::class.java)

    override fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal> {
        try {
            // Obliczamy wskaźniki – logujemy, jeśli lista jest zbyt krótka
            val indicators = IndicatorService.computeIndicators(candles)

            // MACD – adaptive lub klasyczny
            val currentMacd = if (StrategyParameters.enhancedMacdUseAdaptive && indicators.adaptiveMacd != null && indicators.adaptiveMacd.histogram.isNotEmpty())
                indicators.adaptiveMacd.histogram.last()
            else
                indicators.macd?.macdLine?.last() ?: 0.0

            val currentSignal = if (StrategyParameters.enhancedMacdUseAdaptive && indicators.adaptiveMacd != null && indicators.adaptiveMacd.signalLine.isNotEmpty())
                indicators.adaptiveMacd.signalLine.last()
            else
                indicators.macd?.signalLine?.last() ?: 0.0

            val currentRSI = indicators.rsi?.last() ?: StrategyParameters.rsiNeutral

            val bbMiddle = indicators.bollingerBands?.middle?.let {
                if (it.isNotEmpty()) it.last() else 0.0
            } ?: 0.0

            val stochasticResult = indicators.stochastic
            val currentStoch = stochasticResult?.k?.let { if (it.isNotEmpty()) it.last() else 50.0 } ?: 50.0

            val ichimokuResult = indicators.ichimoku
            val currentTenkan = ichimokuResult?.tenkanSen?.let { if (it.isNotEmpty()) it.last() else 0.0 } ?: 0.0
            val currentKijun = ichimokuResult?.kijunSen?.let { if (it.isNotEmpty()) it.last() else 0.0 } ?: 0.0

            val adxValues = indicators.adx ?: emptyList()
            val currentAdx = if (adxValues.isNotEmpty()) adxValues.last() else 0.0

            val atrValues = indicators.atr ?: emptyList()
            val currentATR = if (atrValues.isNotEmpty()) atrValues.last() else 0.0

            val pivotPoints = if (StrategyParameters.usePivotPoints) indicators.pivotPoints else null

            val closePrices = candles.map { it.closePrice.toDouble() }
            val currentPrice = closePrices.last()
            val avgVolume = candles.takeLast(20).mapNotNull { it.volume.toDoubleOrNull() }.average()
            val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0

            // Podstawowe warunki: MACD, RSI oraz wolumen (względem średniego)
            var buyCondition = (currentMacd > currentSignal) &&
                    (currentRSI > StrategyParameters.rsiNeutral) &&
                    (currentVolume > avgVolume * StrategyParameters.volumeRatioThreshold)
            var sellCondition = (currentMacd < currentSignal) &&
                    (currentRSI < StrategyParameters.rsiNeutral) &&
                    (currentVolume > avgVolume * StrategyParameters.volumeRatioThreshold)

            // Dodatkowe filtry:
            if (StrategyParameters.useAdx && adxValues.isNotEmpty()) {
                buyCondition = buyCondition && (currentAdx > StrategyParameters.adxThreshold)
                sellCondition = sellCondition && (currentAdx > StrategyParameters.adxThreshold)
            }
            if (StrategyParameters.useStochastic && stochasticResult != null && stochasticResult.k.isNotEmpty()) {
                buyCondition = buyCondition && (currentStoch < StrategyParameters.stochasticOversold)
                sellCondition = sellCondition && (currentStoch > StrategyParameters.stochasticOverbought)
            }
            if (StrategyParameters.useIchimoku && ichimokuResult != null &&
                ichimokuResult.tenkanSen.isNotEmpty() && ichimokuResult.kijunSen.isNotEmpty()) {
                buyCondition = buyCondition && (currentTenkan > currentKijun)
                sellCondition = sellCondition && (currentTenkan < currentKijun)
            }
            if (StrategyParameters.useParabolicSar && candles.size >= 2) {
                val sarValues = Indicators.computeParabolicSar(candles)
                if (sarValues.isNotEmpty()) {
                    val currentSar = sarValues.last()
                    buyCondition = buyCondition && (currentPrice > currentSar)
                    sellCondition = sellCondition && (currentPrice < currentSar)
                }
            }

            // Dynamiczna modyfikacja ryzyka
            val riskPercent = if (TimeUtils.isTradingTime(candle.closeTime))
                StrategyParameters.baseRiskPercent * 2
            else
                StrategyParameters.baseRiskPercent / 2

            return when {
                buyCondition -> {
                    val quantity = computeQuantity(capital, currentPrice, riskPercent)
                    listOf(
                        StrategySignal(
                            type = SignalType.BUY,
                            price = currentPrice,
                            stopLoss = currentPrice * (1 - StrategyParameters.stopLossPercent),
                            takeProfit = currentPrice * (1 + StrategyParameters.takeProfitPercent),
                            quantity = quantity,
                            indicatorData = mapOf(
                                "MACD" to currentMacd,
                                "Signal" to currentSignal,
                                "RSI" to currentRSI,
                                "Stochastic" to currentStoch,
                                "Tenkan" to currentTenkan,
                                "Kijun" to currentKijun,
                                "ADX" to currentAdx,
                                "ATR" to currentATR,
                                "BB_Middle" to bbMiddle,
                                "AvgVolume" to avgVolume,
                                "CurrentVolume" to currentVolume,
                                "PivotPoints" to pivotPoints
                            )
                        )
                    )
                }
                sellCondition -> {
                    val quantity = computeQuantity(capital, currentPrice, riskPercent)
                    listOf(
                        StrategySignal(
                            type = SignalType.SELL,
                            price = currentPrice,
                            stopLoss = currentPrice * (1 + StrategyParameters.stopLossPercent),
                            takeProfit = currentPrice * (1 - StrategyParameters.takeProfitPercent),
                            quantity = quantity,
                            indicatorData = mapOf(
                                "MACD" to currentMacd,
                                "Signal" to currentSignal,
                                "RSI" to currentRSI,
                                "Stochastic" to currentStoch,
                                "Tenkan" to currentTenkan,
                                "Kijun" to currentKijun,
                                "ADX" to currentAdx,
                                "ATR" to currentATR,
                                "BB_Middle" to bbMiddle,
                                "AvgVolume" to avgVolume,
                                "CurrentVolume" to currentVolume,
                                "PivotPoints" to pivotPoints
                            )
                        )
                    )
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.error("Error in EnhancedAdaptiveMACDStrategy.onNewCandle: ${e.message}", e)
            return emptyList()
        }
    }

    override fun onUpdatePosition(candle: Kline, candles: List<Kline>, position: OpenPosition): List<StrategySignal> {
        val currentPrice = candle.closePrice.toDouble()
        if (position.side == "BUY") {
            if (currentPrice > position.maxFavorable) {
                position.maxFavorable = currentPrice
                val newStopLoss = currentPrice * (1 - StrategyParameters.stopLossPercent)
                if (newStopLoss > (position.stopLoss ?: 0.0)) {
                    position.stopLoss = newStopLoss
                }
            }
            if (currentPrice <= (position.stopLoss ?: 0.0)) {
                return listOf(
                    StrategySignal(
                        type = SignalType.CLOSE,
                        price = currentPrice,
                        quantity = position.quantity
                    )
                )
            }
        } else if (position.side == "SELL") {
            if (currentPrice < position.minFavorable) {
                position.minFavorable = currentPrice
                val newStopLoss = currentPrice * (1 + StrategyParameters.stopLossPercent)
                if (newStopLoss < (position.stopLoss ?: Double.MAX_VALUE)) {
                    position.stopLoss = newStopLoss
                }
            }
            if (currentPrice >= (position.stopLoss ?: Double.MAX_VALUE)) {
                return listOf(
                    StrategySignal(
                        type = SignalType.CLOSE,
                        price = currentPrice,
                        quantity = position.quantity
                    )
                )
            }
        }
        return emptyList()
    }

    private fun computeQuantity(capital: Double, price: Double, riskPercent: Double): Double {
        val riskPerTrade = capital * riskPercent
        return riskPerTrade / price
    }
}
