package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min

/**
 * Przykładowa strategia RSI Overbought/Oversold + Trend:
 * - Obliczamy RSI
 * - BUY, gdy RSI < rsiBuyThreshold (i ewentualnie trend jest UP)
 * - SELL, gdy RSI > rsiSellThreshold (i ewentualnie trend jest DOWN)
 */
class RSIOverboughtOversoldTrendStrategy(
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.isEmpty()) return signals

        val close = closeList.lastOrNull() ?: return signals

        // Oblicz RSI
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiArr.lastOrNull() ?: 50.0

        // (opcjonalnie) Jakiś prosty trend check:
        // np. porównaj close z prostą średnią
        // val smaArr = Indicators.computeSma(closeList, 50)
        // val sma = smaArr.lastOrNull() ?: close
        // val isUpTrend = (close > sma)

        // BUY, jeśli RSI < rsiBuyThreshold
        if (rsi < rsiBuyThreshold) {
            val stopLoss = close - (close * 0.005)
            val takeProfit = close + (close * 0.01)

            // Przykładowe obliczenie rawQty:
            val rawQty = (capital * 0.02) / (close * 0.005)

            // Klamp do 0.002
            val maxQty = 0.002
            val qty = min(rawQty, maxQty)

            signals.add(
                StrategySignal(
                    SignalType.BUY,
                    close,
                    stopLoss,
                    takeProfit,
                    qty
                )
            )
        }
        // SELL, jeśli RSI > rsiSellThreshold
        else if (rsi > rsiSellThreshold) {
            val stopLoss = close + (close * 0.005)
            val takeProfit = close - (close * 0.01)

            val rawQty = (capital * 0.02) / (close * 0.005)
            val maxQty = 0.002
            val qty = min(rawQty, maxQty)

            signals.add(
                StrategySignal(
                    SignalType.SELL,
                    close,
                    stopLoss,
                    takeProfit,
                    qty
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

        // Minimal trailing offset
        val offset = price * 0.003
        when (openPosition.side) {
            "BUY" -> {
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset
                if (price <= trailingStop ||
                    price >= openPosition.takeProfit ||
                    price <= openPosition.stopLoss
                ) {
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
                val trailingStop = openPosition.minFavorable + offset
                if (price >= trailingStop ||
                    price <= openPosition.takeProfit ||
                    price >= openPosition.stopLoss
                ) {
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
