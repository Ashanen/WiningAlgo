package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import kotlin.math.min

class AdaptiveMACDStrategy(
    private val shortPeriod: Int = 12,
    private val longPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    private val atrPeriod: Int = 14,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val atrMultiplierSL: Double = 1.5,
    private val atrMultiplierTP: Double = 3.0
) : Strategy {

    override val name: String = "AdaptiveMACDStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        if (!TimeUtils.isTradingTime(candle.closeTime)) return emptyList()
        if (candles.size < longPeriod + signalPeriod) return emptyList()

        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < longPeriod + signalPeriod) return emptyList()

        val macdResult = Indicators.computeMacd(closePrices, shortPeriod, longPeriod, signalPeriod)
        if (macdResult.macdLine.size < 2 || macdResult.signalLine.size < 2) return emptyList()

        val currentMacd = macdResult.macdLine.last()
        val currentSignal = macdResult.signalLine.last()
        val prevMacd = macdResult.macdLine[macdResult.macdLine.size - 2]
        val prevSignal = macdResult.signalLine[macdResult.signalLine.size - 2]

        val atrValues = Indicators.computeAtr(candles, atrPeriod)
        if (atrValues.isEmpty()) return emptyList()
        val currentATR = atrValues.last()

        val currentPrice = closePrices.last()
        val riskAmount = min(capital * riskPercent, maxRiskUsd)
        val signals = mutableListOf<StrategySignal>()

        if (prevMacd < prevSignal && currentMacd > currentSignal) {
            val stopLoss = currentPrice - atrMultiplierSL * currentATR
            val takeProfit = currentPrice + atrMultiplierTP * currentATR
            val riskPerUnit = currentPrice - stopLoss
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
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
        } else if (prevMacd > prevSignal && currentMacd < currentSignal) {
            val stopLoss = currentPrice + atrMultiplierSL * currentATR
            val takeProfit = currentPrice - atrMultiplierTP * currentATR
            val riskPerUnit = stopLoss - currentPrice
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
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
        val trailingOffset = atrMultiplierSL * currentATR
        val signals = mutableListOf<StrategySignal>()

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - trailingOffset
                if (price <= trailingStop || (openPosition.takeProfit != null && price >= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            stopLoss = null,
                            takeProfit = null,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + trailingOffset
                if (price >= trailingStop || (openPosition.takeProfit != null && price <= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = price,
                            stopLoss = null,
                            takeProfit = null,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
        }
        return signals
    }
}