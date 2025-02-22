package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min

/**
 * AdaptiveMACDStrategy – strategia oparta na MACD, gdzie:
 * - Sygnał BUY generowany jest, gdy MACD przecina linię sygnału od dołu.
 * - Sygnał SELL generowany jest, gdy MACD przecina linię sygnału od góry.
 * Poziomy SL/TP ustalane są dynamicznie na podstawie ATR.
 */
class AdaptiveMACDStrategy(
    private val shortPeriod: Int = 12,
    private val longPeriod: Int = 26,
    private val signalPeriod: Int = 9,
    private val atrPeriod: Int = 14,
    private val riskPercent: Double = 0.02,  // 2% kapitału
    private val maxRiskUsd: Double = 100.0,    // maksymalna kwota ryzyka
    private val atrMultiplierSL: Double = 1.5, // stop-loss = currentPrice - 1.5 * ATR (dla BUY)
    private val atrMultiplierTP: Double = 3.0  // take-profit = currentPrice + 3.0 * ATR (dla BUY)
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

        // Obliczamy MACD i linię sygnału
        val (macd, signalLine) = Indicators.computeMacd(closePrices, shortPeriod, longPeriod, signalPeriod)
        if (macd.size < 2 || signalLine.size < 2) return signals

        val currentMacd = macd.last()
        val currentSignal = signalLine.last()
        val prevMacd = macd[macd.size - 2]
        val prevSignal = signalLine[signalLine.size - 2]

        // Obliczamy ATR dla dynamicznego ustawienia SL/TP
        val atrValues = Indicators.computeAtr(candles, atrPeriod)
        if (atrValues.isEmpty()) return signals
        val currentATR = atrValues.last()

        val currentPrice = closePrices.last()
        // Wyliczamy maksymalne ryzyko w USD
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // Warunek kupna – przecięcie MACD od dołu
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
        }
        // Warunek sprzedaży – przecięcie MACD od góry
        if (prevMacd > prevSignal && currentMacd < currentSignal) {
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
        // Implementacja trailing stopu – podobnie jak w istniejących strategiach
        val signals = mutableListOf<StrategySignal>()
        val currentPrice = candle.closePrice.toDoubleOrNull() ?: return signals
        // Obliczamy ATR na podstawie ostatnich kilku świec (można ulepszyć)
        val atrValues = Indicators.computeAtr(listOf(candle), atrPeriod)
        val currentATR = if (atrValues.isNotEmpty()) atrValues.last() else 0.0
        val trailingOffset = atrMultiplierSL * currentATR

        when (openPosition.side) {
            "BUY" -> {
                if (currentPrice > openPosition.maxFavorable) {
                    openPosition.maxFavorable = currentPrice
                }
                val trailingStop = openPosition.maxFavorable - trailingOffset
                if (currentPrice <= trailingStop || (openPosition.takeProfit != null && currentPrice >= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = currentPrice,
                            stopLoss = 0.0,
                            takeProfit = 0.0,
                            quantity = openPosition.quantity
                        )
                    )
                }
            }
            "SELL" -> {
                if (currentPrice < openPosition.minFavorable) {
                    openPosition.minFavorable = currentPrice
                }
                val trailingStop = openPosition.minFavorable + trailingOffset
                if (currentPrice >= trailingStop || (openPosition.takeProfit != null && currentPrice <= openPosition.takeProfit)) {
                    signals.add(
                        StrategySignal(
                            type = SignalType.CLOSE,
                            price = currentPrice,
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
