package strategy

import compute.Indicators
import config.StrategyParameters
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal

class RSIOverboughtOversoldTrendStrategy(
    private val params: StrategyParameters.RSIOverboughtOversoldParams
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        if (!TimeUtils.isTradingTime(candle.closeTime)) return emptyList()
        if (candles.size < params.emaPeriod) return emptyList()

        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < params.emaPeriod) return emptyList()

        val rsi = Indicators.computeRsi(closePrices, params.rsiPeriod)
        if (rsi.isEmpty()) return emptyList()
        val ema = Indicators.computeEma(closePrices, params.emaPeriod)
        val atrValues = Indicators.computeAtr(candles, params.atrPeriod)
        if (atrValues.isEmpty()) return emptyList()

        val currentRSI = rsi.last()
        val currentEMA = ema.last()
        val currentATR = atrValues.last()
        val currentPrice = closePrices.last()
        val riskPercent = if (TimeUtils.isTradingTime(candle.closeTime)) params.baseRiskPercent * 2 else params.baseRiskPercent / 2
        val signals = mutableListOf<StrategySignal>()

        if (currentRSI < params.oversold && currentPrice > currentEMA) {
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
                        quantity = quantity
                    )
                )
            }
        } else if (currentRSI > params.overbought && currentPrice < currentEMA) {
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
                        quantity = quantity
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