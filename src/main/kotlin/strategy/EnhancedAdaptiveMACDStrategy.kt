package strategy

import compute.Indicators
import compute.Indicators.IchimokuResult
import compute.Indicators.StochasticResult
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import TimeUtils

class EnhancedAdaptiveMACDStrategy(
    private val fastPeriod: Int,
    private val slowPeriod: Int,
    private val signalPeriod: Int,
    private val rsiPeriod: Int,
    private val atrPeriod: Int,
    private val baseRiskPercent: Double,
    private val atrMultiplierSL: Double,
    private val atrMultiplierTP: Double,
    private val useAdaptive: Boolean,
    private val useStochastic: Boolean,
    private val stochasticPeriod: Int,
    private val stochasticDPeriod: Int,
    private val stochasticOverbought: Double,
    private val stochasticOversold: Double,
    private val useAdx: Boolean,
    private val adxPeriod: Int,
    private val adxThreshold: Double,
    private val useIchimoku: Boolean,
    private val tenkanPeriod: Int,
    private val kijunPeriod: Int,
    private val senkouSpanBPeriod: Int,
    private val ichimokuDisplacement: Int,
    private val useParabolicSar: Boolean
) : Strategy {

    override val name: String = "EnhancedAdaptiveMACDStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        if (!TimeUtils.isTradingTime(candle.closeTime)) return emptyList()
        if (candles.size < slowPeriod) return emptyList()

        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < slowPeriod) return emptyList()

        // MACD – adaptacyjny lub klasyczny
        val macdResult = if (useAdaptive) {
            Indicators.computeAdaptiveMacd(closePrices, fastPeriod, slowPeriod, signalPeriod)
        } else {
            Indicators.computeMacd(closePrices, fastPeriod, slowPeriod, signalPeriod)
        }
        if (macdResult.macdLine.size < 2 || macdResult.signalLine.size < 2) return emptyList()

        val rsi = Indicators.computeRsi(closePrices, rsiPeriod)
        if (rsi.isEmpty()) return emptyList()

        val atrValues = Indicators.computeAtr(candles, atrPeriod)
        if (atrValues.isEmpty()) return emptyList()

        val currentMacd = macdResult.macdLine.last()
        val currentSignal = macdResult.signalLine.last()
        val currentRSI = rsi.last()
        val currentATR = atrValues.last()
        val currentPrice = closePrices.last()
        val avgVolume = candles.takeLast(20).mapNotNull { it.volume.toDoubleOrNull() }.average()
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0

        // Dodatkowe wskaźniki:
        var stochastic: StochasticResult? = null
        if (useStochastic && candles.size >= stochasticPeriod) {
            stochastic = Indicators.computeStochasticOscillator(candles, stochasticPeriod, stochasticDPeriod)
        }
        val adxValues = if (useAdx) Indicators.computeAdx(candles, adxPeriod) else emptyList()
        val ichimoku: IchimokuResult? = if (useIchimoku && candles.size >= senkouSpanBPeriod) {
            Indicators.computeIchimoku(candles, tenkanPeriod, kijunPeriod, senkouSpanBPeriod, ichimokuDisplacement)
        } else null

        // Ustalanie ryzyka – modyfikacja bazowa
        val riskPercent = if (TimeUtils.isTradingTime(candle.closeTime)) baseRiskPercent * 2 else baseRiskPercent / 2

        // Główne warunki – baza na MACD, RSI i wolumenie
        var buyCondition = currentMacd > currentSignal && currentRSI > 50 && currentVolume > avgVolume
        var sellCondition = currentMacd < currentSignal && currentRSI < 50 && currentVolume > avgVolume

        // Filtry dodatkowe:
        if (useAdx && adxValues.isNotEmpty()) {
            val currentAdx = adxValues.last()
            buyCondition = buyCondition && (currentAdx > adxThreshold)
            sellCondition = sellCondition && (currentAdx > adxThreshold)
        }
        if (useStochastic && stochastic != null && stochastic.k.isNotEmpty()) {
            val currentStoch = stochastic.k.last()
            buyCondition = buyCondition && (currentStoch < stochasticOversold)
            sellCondition = sellCondition && (currentStoch > stochasticOverbought)
        }
        if (useIchimoku && ichimoku != null && ichimoku.tenkanSen.isNotEmpty() && ichimoku.kijunSen.isNotEmpty()) {
            val currentTenkan = ichimoku.tenkanSen.last()
            val currentKijun = ichimoku.kijunSen.last()
            buyCondition = buyCondition && (currentTenkan > currentKijun)
            sellCondition = sellCondition && (currentTenkan < currentKijun)
        }
        if (useParabolicSar && candles.size >= 2) {
            val sarValues = Indicators.computeParabolicSar(candles)
            if (sarValues.isNotEmpty()) {
                val currentSar = sarValues.last()
                buyCondition = buyCondition && (currentPrice > currentSar)
                sellCondition = sellCondition && (currentPrice < currentSar)
            }
        }

        val signals = mutableListOf<StrategySignal>()
        if (buyCondition) {
            val stopLoss = currentPrice - atrMultiplierSL * currentATR
            val takeProfit = currentPrice + atrMultiplierTP * currentATR
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
            val stopLoss = currentPrice + atrMultiplierSL * currentATR
            val takeProfit = currentPrice - atrMultiplierTP * currentATR
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
        val atrValues = Indicators.computeAtr(candles, atrPeriod)
        if (atrValues.isEmpty()) return emptyList()
        val currentATR = atrValues.last()
        val atrMultiplier = if (TimeUtils.isTradingTime(candle.closeTime)) atrMultiplierSL else 1.0
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
