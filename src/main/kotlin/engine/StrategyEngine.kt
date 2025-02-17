package engine

import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import org.slf4j.LoggerFactory
import strategy.Strategy

/**
 * Silnik backtest/real
 */
class StrategyEngine(
    private val tradeExecutor: TradeExecutor,
    private val strategy: Strategy
) {
    private val logger = LoggerFactory.getLogger(StrategyEngine::class.java)

    var capital: Double = 1000.0
    private var openPosition: OpenPosition? = null
    private var lastCandleIndex = -1

    // Używamy do liczenia indeksów
    private val processedCandles = mutableListOf<Kline>()

    /**
     * Metoda wywoływana przy każdej nowej świecy
     */
    fun processCandle(candle: Kline) {
        processedCandles.add(candle)
        val currentIndex = processedCandles.size - 1
        if (!candle.isClosed) return  // czekamy na zamknięcie świecy

        // 1) Jeśli nie mamy pozycji, sprawdzamy sygnały onNewCandle
        if (openPosition == null) {
            val signals = strategy.onNewCandle(
                candle,
                processedCandles, // lub processedCandles.take(currentIndex+1)
                capital
            )
            // otwieramy pozycję TYLKO jeśli sygnał jest BUY/SELL
            signals.forEach { sig ->
                if (sig.type == SignalType.BUY || sig.type == SignalType.SELL) {
                    val sideName = sig.type.name  // "BUY"/"SELL"
                    openPosition = OpenPosition(
                        side = sideName,
                        entryPrice = sig.price,
                        stopLoss = sig.stopLoss,
                        takeProfit = sig.takeProfit,
                        quantity = sig.quantity,
                        maxFavorable = sig.price,
                        minFavorable = sig.price
                    )
                    logger.info("Opened position: {}, capital={}", openPosition, capital)

                    // Wywołanie real/sim trade executor
                    val ok = tradeExecutor.openTrade(
                        sideName,
                        sig.quantity,
                        sig.price,
                        sig.stopLoss,
                        sig.takeProfit
                    )
                    if (!ok) {
                        logger.error("Error opening trade via Executor.")
                    }
                }
            }
            // UWAGA: Nie wywołujemy onUpdatePosition w tej samej świecy => brak natychmiastowego zamknięcia
        }
        // 2) Jeśli mamy otwartą pozycję => sprawdzamy onUpdatePosition
        else {
            val exitSignals = strategy.onUpdatePosition(candle, openPosition!!)
            exitSignals.forEach { sig ->
                if (sig.type == SignalType.CLOSE) {
                    // Zamykamy pozycję
                    val exitPrice = sig.price
                    val profit = if (openPosition!!.side == "BUY") {
                        (exitPrice - openPosition!!.entryPrice) * openPosition!!.quantity
                    } else {
                        (openPosition!!.entryPrice - exitPrice) * openPosition!!.quantity
                    }
                    capital += profit
                    logger.info("Closed position with profit={}, new capital={}", profit, capital)

                    // Wywołanie tradeExecutor
                    val ok = tradeExecutor.closeTrade(
                        openPosition!!.side,
                        openPosition!!.quantity,
                        exitPrice
                    )
                    if (!ok) {
                        logger.error("Error closing trade via Executor.")
                    }

                    openPosition = null
                }
            }
        }
    }
}
