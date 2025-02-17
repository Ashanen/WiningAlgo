package engine

import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import org.slf4j.LoggerFactory
import strategy.Strategy

/**
 * Podstawowy silnik, który:
 *  - trzyma capital,
 *  - trzyma openPosition (tylko 1 na raz),
 *  - wywołuje onNewCandle / onUpdatePosition,
 *  - wysyła zlecenia do tradeExecutor (Real / Simulation)
 */
class StrategyEngine(
    private val tradeExecutor: TradeExecutor,
    private val strategy: Strategy
) {
    private val logger = LoggerFactory.getLogger(StrategyEngine::class.java)

    var capital: Double = 1000.0
    private var openPosition: OpenPosition? = null

    private val processedCandles = mutableListOf<Kline>()

    fun processCandle(candle: Kline) {
        processedCandles.add(candle)

        // czekamy, aż świeca się zamknie
        if (!candle.isClosed) return

        // jeśli nie ma pozycji, sprawdzamy sygnały
        if (openPosition == null) {
            val signals = strategy.onNewCandle(candle, processedCandles, capital)
            signals.forEach { sig ->
                if (sig.type == SignalType.BUY || sig.type == SignalType.SELL) {
                    val sideName = sig.type.name
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
        } else {
            // mamy pozycję => sprawdzamy trailing / exit
            val exitSignals = strategy.onUpdatePosition(candle, openPosition!!)
            exitSignals.forEach { sig ->
                if (sig.type == SignalType.CLOSE) {
                    val exitPrice = sig.price
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
}
