package strategy

import config.StrategyParameters
import indicator.IndicatorService
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import compute.Indicators

/**
 * Klasa EnhancedAdaptiveMACDStrategy wykorzystuje rozszerzony zestaw wskaźników:
 * MACD/adaptive MACD, RSI, Bollinger Bands, Stochastic, Ichimoku, ADX, ATR oraz PivotPoints.
 *
 * Warunki wejścia są ustalane na podstawie:
 * - Podstawowych warunków: MACD (adaptive lub klasyczny), RSI (z warunkiem neutralności) oraz wolumenu,
 * - Dodatkowych filtrów: ADX, Stochastic, Ichimoku oraz Parabolic SAR.
 *
 * Wszystkie wartości są konfigurowalne przez StrategyParameters.
 */
class EnhancedAdaptiveMACDStrategy(
    override val name: String = "EnhancedAdaptiveMACDStrategy"
) : Strategy {

    override fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal> {
        // Obliczamy wszystkie wskaźniki
        val indicators = IndicatorService.computeIndicators(candles)

        // Wybieramy MACD – adaptive lub klasyczny
        val currentMacd = if (StrategyParameters.enhancedMacdUseAdaptive && indicators.adaptiveMacd != null && indicators.adaptiveMacd.histogram.isNotEmpty())
            indicators.adaptiveMacd.histogram.last()
        else
            indicators.macd?.macdLine?.last() ?: 0.0

        val currentSignal = if (StrategyParameters.enhancedMacdUseAdaptive && indicators.adaptiveMacd != null && indicators.adaptiveMacd.signalLine.isNotEmpty())
            indicators.adaptiveMacd.signalLine.last()
        else
            indicators.macd?.signalLine?.last() ?: 0.0

        val currentRSI = indicators.rsi?.last() ?: StrategyParameters.rsiNeutral

        // Bollinger Bands – obliczamy środkową wstęgę jako dodatkową informację
        val bbMiddle = indicators.bollingerBands?.middle?.last() ?: 0.0

        // Dodatkowe wskaźniki:
        val stochasticResult = indicators.stochastic
        val currentStoch = stochasticResult?.k?.last() ?: 50.0  // Jeśli brak danych, domyślnie 50

        val ichimokuResult = indicators.ichimoku
        val currentTenkan = ichimokuResult?.tenkanSen?.last() ?: 0.0
        val currentKijun = ichimokuResult?.kijunSen?.last() ?: 0.0

        val adxValues = indicators.adx ?: emptyList()
        val currentAdx = if (adxValues.isNotEmpty()) adxValues.last() else 0.0

        val atrValues = indicators.atr ?: emptyList()
        val currentATR = if (atrValues.isNotEmpty()) atrValues.last() else 0.0

        val pivotPoints = if (StrategyParameters.usePivotPoints) indicators.pivotPoints else null

        // Podstawowe warunki – MACD, RSI oraz wolumen
        val closePrices = candles.map { it.closePrice.toDouble() }
        val currentPrice = closePrices.last()
        val avgVolume = candles.takeLast(20).mapNotNull { it.volume.toDoubleOrNull() }.average()
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0

        // Używamy parametru volumeRatioThreshold, aby porównać bieżący wolumen z średnim
        var buyCondition = (currentMacd > currentSignal) &&
                (currentRSI > StrategyParameters.rsiNeutral) &&
                (currentVolume > avgVolume * StrategyParameters.volumeRatioThreshold)
        var sellCondition = (currentMacd < currentSignal) &&
                (currentRSI < StrategyParameters.rsiNeutral) &&
                (currentVolume > avgVolume * StrategyParameters.volumeRatioThreshold)

        // Filtr ADX
        if (StrategyParameters.useAdx && adxValues.isNotEmpty()) {
            buyCondition = buyCondition && (currentAdx > StrategyParameters.adxThreshold)
            sellCondition = sellCondition && (currentAdx > StrategyParameters.adxThreshold)
        }
        // Filtr stochastyczny
        if (StrategyParameters.useStochastic && stochasticResult != null && stochasticResult.k.isNotEmpty()) {
            buyCondition = buyCondition && (currentStoch < StrategyParameters.stochasticOversold)
            sellCondition = sellCondition && (currentStoch > StrategyParameters.stochasticOverbought)
        }
        // Filtr Ichimoku – warunek kupna: Tenkan > Kijun; sprzedaży: Tenkan < Kijun
        if (StrategyParameters.useIchimoku && ichimokuResult != null &&
            ichimokuResult.tenkanSen.isNotEmpty() && ichimokuResult.kijunSen.isNotEmpty()) {
            buyCondition = buyCondition && (currentTenkan > currentKijun)
            sellCondition = sellCondition && (currentTenkan < currentKijun)
        }
        // Filtr Parabolic SAR
        if (StrategyParameters.useParabolicSar && candles.size >= 2) {
            val sarValues = Indicators.computeParabolicSar(candles)
            if (sarValues.isNotEmpty()) {
                val currentSar = sarValues.last()
                buyCondition = buyCondition && (currentPrice > currentSar)
                sellCondition = sellCondition && (currentPrice < currentSar)
            }
        }

        // Ustalanie ryzyka – dynamiczne w zależności od pory handlu
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
