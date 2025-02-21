package strategy

import compute.Indicators
import model.Kline
import model.OpenPosition
import model.StrategySignal
import model.SignalType
import kotlin.math.min
import kotlin.math.max

/**
 * Przykładowa strategia oparta na RSI (overbought/oversold),
 * z podstawowym SL/TP oraz trailing stop.
 */
class RSIOverboughtOversoldTrendStrategy(
    private val rsiPeriod: Int = 14,
    private val rsiBuyThreshold: Double = 30.0,
    private val rsiSellThreshold: Double = 70.0,
    private val riskPercent: Double = 0.02,  // 2% kapitału
    private val maxRiskUsd: Double = 100.0,  // maksymalna kwota ryzyka w USD
    private val slPct: Double = 0.015,       // np. 1.5% stop-loss
    private val tpPct: Double = 0.03,        // np. 3.0% take-profit
    private val trailingOffsetPct: Double = 0.008 // np. 0.8% trailing offset
) : Strategy {

    override val name: String = "RSIOverboughtOversoldTrendStrategy"

    /**
     * Metoda wywoływana przy zamknięciu każdej nowej świecy (backtest/real).
     * Zwraca listę sygnałów (BUY/SELL lub pustą), zależnie od warunków RSI.
     */
    override fun onNewCandle(
        candle: Kline,
        candles: List<Kline>,
        capital: Double
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()

        // Konwertujemy closePrice na listę Double
        val closeList = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closeList.size < rsiPeriod) return signals

        // Obliczamy RSI
        val rsiArr = Indicators.computeRsi(closeList, rsiPeriod)
        val i = closeList.lastIndex
        if (i < rsiPeriod - 1) return signals

        val close = closeList[i]
        val rsi = rsiArr[i]

        // Wyliczamy maksymalne ryzyko w USD
        val riskAmount = min(capital * riskPercent, maxRiskUsd)

        // Przykład:
        // - RSI < rsiBuyThreshold => sygnał BUY
        // - RSI > rsiSellThreshold => sygnał SELL

        if (rsi < rsiBuyThreshold) {
            // Stop-loss i take-profit liczone procentowo od aktualnej ceny
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

    /**
     * Metoda wywoływana, gdy mamy już otwartą pozycję. Służy do sprawdzenia
     * warunków trailing stop, SL/TP i ewentualnego zamknięcia pozycji.
     */
    override fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal> {
        val signals = mutableListOf<StrategySignal>()
        val price = candle.closePrice.toDoubleOrNull() ?: return signals

        // trailingOffsetPct -> liczymy offset trailing stop
        val offset = price * trailingOffsetPct

        when (openPosition.side) {
            "BUY" -> {
                // Aktualizujemy maxFavorable
                if (price > openPosition.maxFavorable) {
                    openPosition.maxFavorable = price
                }
                val trailingStop = openPosition.maxFavorable - offset

                // Ponieważ stopLoss i takeProfit mogą być null,
                // najpierw sprawdzamy czy nie są null, a potem porównujemy.
                val stopLossVal = openPosition.stopLoss
                val takeProfitVal = openPosition.takeProfit

                val closeCondition =
                    // trailing stop
                    (price <= trailingStop) ||
                            // takeProfit
                            (takeProfitVal != null && price >= takeProfitVal) ||
                            // stopLoss
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
                // Aktualizujemy minFavorable
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
