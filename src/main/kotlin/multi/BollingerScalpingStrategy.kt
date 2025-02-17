package multi

import compute.Indicators
import convert.toCloseDouble
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import strategy.Strategy

class BollingerScalpingStrategy(
    private val bbPeriod: Int = 20,
    private val bbDev: Double = 2.0,
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0
) : Strategy {

    override val name: String = "BollingerScalping"

    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        // Zamieniamy closePrice na listę Double
        val closeList = candles.toCloseDouble()
        // Sprawdzamy minimalną liczbę świec
        if (closeList.size < bbPeriod || closeList.size < rsiPeriod) return signals

        val i = closeList.lastIndex
        if (i < bbPeriod - 1 || i < rsiPeriod) return signals

        // Bollinger
        val bb = Indicators.computeBollingerBands(closeList, bbPeriod, bbDev)
        val lower = bb.lower[i]
        val upper = bb.upper[i]

        // RSI (zakładamy, że Indicators.computeRsi(...) zwraca listę)
        val rsiList = Indicators.computeRsi(closeList, rsiPeriod)
        val rsi = rsiList[i]

        val price = closeList[i]

        // Warunek BUY
        if (!lower.isNaN() && price <= lower && rsi < rsiBuyThreshold) {
            signals.add(
                StrategySignal(
                    type = SignalType.BUY,
                    price = price,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    quantity = 1.0
                )
            )
        }
        // Warunek SELL
        else if (!upper.isNaN() && price >= upper && rsi > rsiSellThreshold) {
            signals.add(
                StrategySignal(
                    type = SignalType.SELL,
                    price = price,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    quantity = 1.0
                )
            )
        }

        return signals
    }

    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        // Na razie brak trailing stop/wyjścia
        return emptyList()
    }
}
