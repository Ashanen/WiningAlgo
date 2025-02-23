package strategy

import model.Kline
import model.OpenPosition
import model.StrategySignal
import compute.Indicators
import model.SignalType
import kotlin.math.min

// Adaptive MACD Strategy
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
        val signals = mutableListOf<StrategySignal>()
        if (candles.size < longPeriod + signalPeriod) return signals

        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < longPeriod + signalPeriod) return signals

        val (macd, signalLine, _) = Indicators.computeMacd(closePrices, shortPeriod, longPeriod, signalPeriod)
        if (macd.size < 2 || signalLine.size < 2) return signals

        val currentMacd = macd.last()
        val currentSignal = signalLine.last()
        val prevMacd = macd[macd.size - 2]
        val prevSignal = signalLine[signalLine.size - 2]

        val atrValues = Indicators.computeAtr(candles, atrPeriod)
        if (atrValues.isEmpty()) return signals
        val currentATR = atrValues.last()

        val currentPrice = closePrices.last()
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

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
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        val atrValues = Indicators.computeAtr(listOf(candle), atrPeriod)
        val currentATR = if (atrValues.isNotEmpty()) atrValues.last() else 0.0
        val trailingOffset = atrMultiplierSL * currentATR

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
                val trailingStop = openPosition.minFavorable + trailingOffset
                if (price >= trailingStop || (openPosition.takeProfit != null && price <= openPosition.takeProfit)) {
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

// RSI Overbought/Oversold Trend Strategy
class RSIOverboughtOversoldTrendStrategy(
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val slPct: Double = 0.015,
    private val tpPct: Double = 0.03,
    private val trailingOffsetPct: Double = 0.008
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < rsiPeriod) return signals

        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val i = closeList.lastIndex
        if (i < rsiPeriod - 1) return signals

        val close = closeList[i]
        val rsi = rsiArr[i]

        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        if (rsi < rsiBuyThreshold) {
            val stopLoss = close * (1.0 - slPct)
            val takeProfit = close * (1.0 + tpPct)
            val riskPerUnit = close - stopLoss
            if (riskPerUnit <= 0.0) return signals

            val quantity = riskAmount / riskPerUnit
            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    quantity = quantity
                )
            )
        } else if (rsi > rsiSellThreshold) {
            val stopLoss = close * (1.0 + slPct)
            val takeProfit = close * (1.0 - tpPct)
            val riskPerUnit = stopLoss - close
            if (riskPerUnit <= 0.0) return signals

            val quantity = riskAmount / riskPerUnit
            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = close,
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
        val offset = price * trailingOffsetPct

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                val stopLossVal = openPosition.stopLoss
                val takeProfitVal = openPosition.takeProfit
                val closeCondition =
                    (price <= trailingStop) ||
                            (takeProfitVal != null && price >= takeProfitVal) ||
                            (stopLossVal != null && price <= stopLossVal)

                if (closeCondition) {
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
                val trailingStop = openPosition.minFavorable + offset
                val stopLossVal = openPosition.stopLoss
                val takeProfitVal = openPosition.takeProfit
                val closeCondition =
                    (price >= trailingStop) ||
                            (takeProfitVal != null && price <= takeProfitVal) ||
                            (stopLossVal != null && price >= stopLossVal)

                if (closeCondition) {
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

// Enhanced Adaptive MACD Strategy
class EnhancedAdaptiveMACDStrategy(
    private val fastPeriod: Int = 12,
    private val slowPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    private val volumeThreshold: Double = 10000.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val slPct: Double = 0.01,
    private val tpPct: Double = 0.02
) : Strategy {

    override val name: String = "EnhancedAdaptiveMACDStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < slowPeriod) return signals

        val macdResult = Indicators.computeMacd(closePrices, fastPeriod, slowPeriod, signalPeriod)
        val latestIndex = closePrices.lastIndex
        val macdValue = macdResult.macd[latestIndex]
        val signalValue = macdResult.signal[latestIndex]
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        if (currentVolume < volumeThreshold) return signals

        val rsiArray = Indicators.computeRsi(closePrices, rsiPeriod)
        val latestRsi = rsiArray[latestIndex]
        val price = closePrices[latestIndex]

        if (macdValue > signalValue && latestRsi < rsiBuyThreshold) {
            val stopLoss = price * (1 - slPct)
            val takeProfit = price * (1 + tpPct)
            val riskPerUnit = price - stopLoss
            if (riskPerUnit > 0) {
                val quantity = min(capital * 0.01 / riskPerUnit, 1.0)
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
        } else if (macdValue < signalValue && latestRsi > rsiSellThreshold) {
            val stopLoss = price * (1 + slPct)
            val takeProfit = price * (1 - tpPct)
            val riskPerUnit = stopLoss - price
            if (riskPerUnit > 0) {
                val quantity = min(capital * 0.01 / riskPerUnit, 1.0)
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
        }
        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        if (openPosition.side == "BUY") {
            if (price > openPosition.maxFavorable) {
                openPosition.maxFavorable = price
            }
            val trailingStop = openPosition.maxFavorable * (1 - slPct)
            if (price <= trailingStop || (openPosition.takeProfit != null && price >= openPosition.takeProfit)) {
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
        } else if (openPosition.side == "SELL") {
            if (price < openPosition.minFavorable) {
                openPosition.minFavorable = price
            }
            val trailingStop = openPosition.minFavorable * (1 + slPct)
            if (price >= trailingStop || (openPosition.takeProfit != null && price <= openPosition.takeProfit)) {
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
        return signals
    }
}

// Bollinger Scalping Strategy
class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val highVolumeThreshold: Double = 5000.0,
    private val minVolumeForSignal: Double = 1000.0
) : Strategy {

    override val name: String = "BollingerScalping"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < bbPeriod) return signals

        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        if (currentVolume < minVolumeForSignal) return signals

        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val i = closeList.lastIndex
        if (i < bbPeriod - 1 || i < rsiPeriod - 1) return signals

        val close = closeList[i]
        val lower = bb.lower[i]
        val upper = bb.upper[i]
        val rsi = rsiArr[i]

        val (slPct, tpPct, _) = if (currentVolume >= highVolumeThreshold) {
            Triple(0.008, 0.06, 0.004)
        } else {
            Triple(0.012, 0.035, 0.0055)
        }
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        if (close <= lower && rsi < rsiBuyThreshold) {
            val stopLoss = close * (1.0 - slPct)
            val takeProfit = close * (1.0 + tpPct)
            val riskPerUnit = close - stopLoss
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = close,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        } else if (close >= upper && rsi > rsiSellThreshold) {
            val stopLoss = close * (1.0 + slPct)
            val takeProfit = close * (1.0 - tpPct)
            val riskPerUnit = stopLoss - close
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = close,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        }
        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        val trailingOffsetPct = if (currentVolume >= highVolumeThreshold) 0.004 else 0.0055
        val offset = price * trailingOffsetPct

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop ||
                    (openPosition.takeProfit != null && price >= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price <= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop ||
                    (openPosition.takeProfit != null && price <= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price >= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
        }
        return signals
    }
}

// Moving Average Cross Strategy
class MovingAverageCrossStrategy(
    private val shortPeriod: Int = 7,
    private val longPeriod: Int = 25,
    private val riskPercent: Double = 0.02,
    private val maxRiskUsd: Double = 100.0,
    private val highVolumeThreshold: Double = 5000.0,
    private val minVolumeForSignal: Double = 500.0
) : Strategy {

    override val name: String = "MovingAverageCrossStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val closePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closePrices.size < longPeriod + 1) return signals

        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        if (currentVolume < minVolumeForSignal) return signals

        val (slPct, tpPct, _) = if (currentVolume >= highVolumeThreshold) {
            Triple(0.008, 0.06, 0.004)
        } else {
            Triple(0.012, 0.045, 0.0055)
        }

        val smaShort = Indicators.computeSma(closePrices, shortPeriod)
        val smaLong = Indicators.computeSma(closePrices, longPeriod)
        val i = closePrices.lastIndex
        if (i < longPeriod) return signals

        val prevDiff = smaShort[i - 1] - smaLong[i - 1]
        val currDiff = smaShort[i] - smaLong[i]
        val price = closePrices[i]
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        if (prevDiff <= 0 && currDiff > 0) {
            val stopLoss = price * (1.0 - slPct)
            val takeProfit = price * (1.0 + tpPct)
            val riskPerUnit = price - stopLoss
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.BUY,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        } else if (prevDiff >= 0 && currDiff < 0) {
            val stopLoss = price * (1.0 + slPct)
            val takeProfit = price * (1.0 - tpPct)
            val riskPerUnit = stopLoss - price
            if (riskPerUnit > 0) {
                val quantity = riskAmount / riskPerUnit
                signals.add(
                    StrategySignal(
                        type = SignalType.SELL,
                        price = price,
                        stopLoss = stopLoss,
                        takeProfit = takeProfit,
                        quantity = quantity,
                        indicatorData = mapOf("volume" to currentVolume, "slPct" to slPct, "tpPct" to tpPct)
                    )
                )
            }
        }
        return signals
    }

    override fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals
        val currentVolume = candle.volume.toDoubleOrNull() ?: 0.0
        val trailingOffsetPct = if (currentVolume >= highVolumeThreshold) 0.004 else 0.0055
        val offset = price * trailingOffsetPct

        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop ||
                    (openPosition.takeProfit != null && price >= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price <= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
            "SELL" -> {
                if (price < openPosition.minFavorable) {
                    openPosition.minFavorable = price
                }
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop ||
                    (openPosition.takeProfit != null && price <= openPosition.takeProfit) ||
                    (openPosition.stopLoss != null && price >= openPosition.stopLoss)
                ) {
                    signals.add(StrategySignal(SignalType.CLOSE, price, indicatorData = mapOf("volume" to currentVolume)))
                }
            }
        }
        return signals
    }
}
