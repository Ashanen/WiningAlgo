package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal

class EnhancedAdaptiveMACDStrategy(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    private val rsiPeriod: Int = 14,
    private val atrPeriod: Int = 14,
    private val baseRiskPercent: Double = 0.01,
    private val atrMultiplierSL: Double = 1.0,
    private val atrMultiplierTP: Double = 3.0
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

        val macdResult = Indicators.computeMacd(closePrices, fastPeriod, slowPeriod, signalPeriod)
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
        val riskPercent = if (TimeUtils.isTradingTime(candle.closeTime)) baseRiskPercent * 2 else baseRiskPercent / 2
        val signals = mutableListOf<StrategySignal>()

        if (currentMacd > currentSignal && currentRSI > 50 && currentVolume > avgVolume * 1.0) {
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
                        quantity = quantity
                    )
                )
            }
        } else if (currentMacd < currentSignal && currentRSI < 50 && currentVolume > avgVolume * 1.0) {
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