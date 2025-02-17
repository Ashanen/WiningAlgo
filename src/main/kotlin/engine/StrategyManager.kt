package engine

import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import org.slf4j.LoggerFactory
import strategy.Strategy

/**
 * StrategyManager - pozwala na użycie wielu strategii równocześnie,
 * ale pilnuje, aby była tylko jedna otwarta pozycja (openPosition) na danym aktywie.
 */
class StrategyManager(
    private val strategies: List<Strategy>,
    private val tradeExecutor: TradeExecutor
) {
    private val logger = LoggerFactory.getLogger(StrategyManager::class.java)

    var capital: Double = 1000.0
    var openPosition: OpenPosition? = null

    /**
     * Wywoływane przy każdej nowej, zamkniętej świecy.
     * @param candle  - świeca, która się właśnie zamknęła
     * @param candles - historia wszystkich świec do tej pory
     */
    fun onNewCandle(candle: Kline, candles: List<Kline>) {
        // Jeśli świeca nie jest zamknięta, nic nie robimy
        if (!candle.isClosed) return

        // Jeżeli nie ma otwartej pozycji => szukamy sygnału BUY/SELL
        if (openPosition == null) {
            val allSignals = mutableListOf<StrategySignal>()
            for (strat in strategies) {
                val signals = strat.onNewCandle(candle, candles, capital)
                allSignals.addAll(signals)
            }
            // Znajdź pierwszy sygnał BUY/SELL
            val openSignal = allSignals.firstOrNull {
                it.type == SignalType.BUY || it.type == SignalType.SELL
            }
            if (openSignal != null) {
                val sideName = openSignal.type.name  // "BUY"/"SELL"
                openPosition = OpenPosition(
                    side         = sideName,
                    entryPrice   = openSignal.price,
                    stopLoss     = openSignal.stopLoss,
                    takeProfit   = openSignal.takeProfit,
                    quantity     = openSignal.quantity,
                    maxFavorable = openSignal.price,
                    minFavorable = openSignal.price
                )
                logger.info("Opened position: {}, capital={}", openPosition, capital)

                // Wywołanie TradeExecutor
                val ok = tradeExecutor.openTrade(
                    sideName,
                    openSignal.quantity,
                    openSignal.price,
                    openSignal.stopLoss,
                    openSignal.takeProfit
                )
                if (!ok) {
                    logger.error("Error opening trade via Executor.")
                }
            }
        }
        // Jeśli jest otwarta pozycja -> sprawdzamy, czy któraś strategia
        // nie chce jej zamknąć (sygnał CLOSE)
        else {
            val exitSignals = mutableListOf<StrategySignal>()
            for (strat in strategies) {
                val s = strat.onUpdatePosition(candle, openPosition!!)
                exitSignals.addAll(s)
            }
            val closeSignal = exitSignals.firstOrNull { it.type == SignalType.CLOSE }
            if (closeSignal != null) {
                // Zamykamy pozycję
                val exitPrice = closeSignal.price
                val profit = if (openPosition!!.side == "BUY") {
                    (exitPrice - openPosition!!.entryPrice) * openPosition!!.quantity
                } else {
                    (openPosition!!.entryPrice - exitPrice) * openPosition!!.quantity
                }
                capital += profit
                logger.info("Closed position with profit={}, new capital={}", profit, capital)

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
