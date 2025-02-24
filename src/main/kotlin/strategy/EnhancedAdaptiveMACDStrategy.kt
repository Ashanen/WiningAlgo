package strategy

import compute.Indicators
import config.StrategyParameters
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import TimeUtils
import compute.IchimokuResult
import compute.StochasticResult

class EnhancedAdaptiveMACDStrategy(
    private val params: StrategyParameters.EnhancedAdaptiveMACDParams
) : Strategy {

    override val name: String = "EnhancedAdaptiveMACDStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        if (!TimeUtils.isTradingTime(candle.closeTime)) return emptyList()
        if (candles.size < params.slowPeriod) return emptyList()

        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < params.slowPeriod) return emptyList()

        val macdResult = if (params.useAdaptive) {
            Indicators.computeAdaptiveMacd(
                closePrices,
                params.fastPeriod,
                params.slowPeriod,
                params.signalPeriod
            )
        } else {
            Indicators.computeMacd(
                closePrices,
                params.fastPeriod,
                params.slowPeriod,
                params.signalPeriod
            )
        }
        if (macdResult.macdLine.size < 2 || macdResult.signalLine.size < 2) return emptyList()

        val rsi = Indicators.computeRsi(closePrices, params.rsiPeriod)
        if (rsi.isEmpty()) return emptyList()

        val atrValues = Indicators.computeAtr(candles, params.atrPeriod)
        if (atrValues.isEmpty()) return emptyList()

        val currentMacd = macdResult.macdLine.last()
        val currentSignal = macdResult.signalLine.last()
        val currentRSI = rsi.last()
        val currentATR = atrValues.last()
        val currentPrice = closePrices.last()
        val avgVolume = candles.takeLast(20).mapNotNull { it.volume.toDoubleOrNull() }.average()
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0

        var stochastic: StochasticResult? = null
        if (params.useStochastic && candles.size >= params.stochasticPeriod) {
            stochastic = Indicators.computeStochasticOscillator(
                candles,
                params.stochasticPeriod,
                params.stochasticDPeriod
            )
        }
        val adxValues = if (params.useAdx) Indicators.computeAdx(candles, params.adxPeriod) else emptyList()
        val ichimoku: IchimokuResult? = if (params.useIchimoku && candles.size >= params.senkouSpanBPeriod) {
            Indicators.computeIchimoku(
                candles,
                params.tenkanPeriod,
                params.kijunPeriod,
                params.senkouSpanBPeriod,
                params.ichimokuDisplacement
            )
        } else null

        val riskPercent = if (TimeUtils.isTradingTime(candle.closeTime)) params.baseRiskPercent * 2 else params.baseRiskPercent / 2

        var buyCondition = currentMacd > currentSignal && currentRSI > 50 && currentVolume > avgVolume
        var sellCondition = currentMacd < currentSignal && currentRSI < 50 && currentVolume > avgVolume

        if (params.useAdx && adxValues.isNotEmpty()) {
            val currentAdx = adxValues.last()
            buyCondition = buyCondition && (currentAdx > params.adxThreshold)
            sellCondition = sellCondition && (currentAdx > params.adxThreshold)
        }
        if (params.useStochastic && stochastic != null && stochastic.k.isNotEmpty()) {
            val currentStoch = stochastic.k.last()
            buyCondition = buyCondition && (currentStoch < params.stochasticOversold)
            sellCondition = sellCondition && (currentStoch > params.stochasticOverbought)
        }
        if (params.useIchimoku && ichimoku != null && ichimoku.tenkanSen.isNotEmpty() && ichimoku.kijunSen.isNotEmpty()) {
            val currentTenkan = ichimoku.tenkanSen.last()
            val currentKijun = ichimoku.kijunSen.last()
            buyCondition = buyCondition && (currentTenkan > currentKijun)
            sellCondition = sellCondition && (currentTenkan < currentKijun)
        }
        if (params.useParabolicSar && candles.size >= 2) {
            val sarValues = Indicators.computeParabolicSar(candles)
            if (sarValues.isNotEmpty()) {
                val currentSar = sarValues.last()
                buyCondition = buyCondition && (currentPrice > currentSar)
                sellCondition = sellCondition && (currentPrice < currentSar)
            }
        }

        val signals = mutableListOf<StrategySignal>()
        if (buyCondition) {
            val stopLoss = currentPrice - params.atrMultiplierSL * currentATR
            val takeProfit = currentPrice + params.atrMultiplierTP * currentATR
            val riskPerUnit = currentPrice - stopLoss
            if (riskPerUnit > 0) {
                val quantity = (capital * riskPercent) / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = currentPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf(
                            "macd" to currentMacd,
                            "signal" to currentSignal,
                            "rsi" to currentRSI,
                            "adx" to if (adxValues.isNotEmpty()) adxValues.last() else null,
                            "stochasticK" to stochastic?.k?.last(),
                            "tenkanSen" to ichimoku?.tenkanSen?.last(),
                            "kijunSen" to ichimoku?.kijunSen?.last()
                        )
                    )
                )
            }
        } else if (sellCondition) {
            val stopLoss = currentPrice + params.atrMultiplierSL * currentATR
            val takeProfit = currentPrice - params.atrMultiplierTP * currentATR
            val riskPerUnit = stopLoss - currentPrice
            if (riskPerUnit > 0) {
                val quantity = (capital * riskPercent) / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = currentPrice,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf(
                            "macd" to currentMacd,
                            "signal" to currentSignal,
                            "rsi" to currentRSI,
                            "adx" to if (adxValues.isNotEmpty()) adxValues.last() else null,
                            "stochasticK" to stochastic?.k?.last(),
                            "tenkanSen" to ichimoku?.tenkanSen?.last(),
                            "kijunSen" to ichimoku?.kijunSen?.last()
                        )
                    )
                )
            }
        }
        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        candles: List<Kline>,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val price = candle.closePrice.toDoubleOrNull() ?: return emptyList()
        val atrValues = Indicators.computeAtr(candles, params.atrPeriod)
        if (atrValues.isEmpty()) return emptyList()
        val currentATR = atrValues.last()
        val atrMultiplier = if (TimeUtils.isTradingTime(candle.closeTime)) params.atrMultiplierSL else 1.0
        val trailingOffset = atrMultiplier * 2.0 * currentATR
        val signals = mutableListOf<StrategySignal>()

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) openPosition.maxFavorable = price
                val trailingStop = openPosition.maxFavorable - trailingOffset
                if (price <= trailingStop || (openPosition.takeProfit != null && price >= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) openPosition.minFavorable = price
                val trailingStop = openPosition.minFavorable + trailingOffset
                if (price >= trailingStop || (openPosition.takeProfit != null && price <= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
        }
        return signals
    }
}