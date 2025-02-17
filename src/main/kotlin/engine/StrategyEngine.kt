package engine


import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import org.slf4j.LoggerFactory
import strategy.SignalType
import strategy.Strategy

class StrategyEngine(
    private val tradeExecutor: TradeExecutor,
    private val strategy: Strategy
) {
    private val logger = LoggerFactory.getLogger(StrategyEngine::class.java)
    val candles = mutableListOf<Kline>()
    var capital = 1000.0
    var openPosition: OpenPosition? = null

    fun processCandle(candle: Kline) {
        // Dodaj świecę
        if (candles.isEmpty() || candle.openTime > candles.last().openTime) {
            candles.add(candle)
            if (candles.size > 2000) candles.removeAt(0)
        }
        if (!candle.isClosed) return

        // sygnały otwarcia
        if (openPosition == null) {
            val signals = strategy.onNewCandle(candle, candles, capital)
            for (signal in signals) {
                if (signal.type == SignalType.BUY || signal.type == SignalType.SELL) {
                    val success = tradeExecutor.openTrade(
                        side = signal.type.name,
                        quantity = signal.quantity,
                        entryPrice = signal.price,
                        stopLoss = signal.stopLoss,
                        takeProfit = signal.takeProfit
                    )
                    if (success) {
                        openPosition = OpenPosition(
                            side = signal.type.name,
                            entryPrice = signal.price,
                            stopLoss = signal.stopLoss,
                            takeProfit = signal.takeProfit,
                            quantity = signal.quantity,
                            maxFavorable = signal.price,
                            minFavorable = signal.price
                        )
                        logger.info("Opened position: $openPosition, capital=$capital")
                    }
                }
            }
        }

        // trailing / exit
        openPosition?.let { pos ->
            val exitSignals = strategy.onUpdatePosition(candle, pos)
            for (signal in exitSignals) {
                if (signal.type == SignalType.CLOSE) {
                    val success = tradeExecutor.closeTrade(
                        openSide = pos.side,
                        quantity = pos.quantity,
                        exitPrice = candle.closePrice.toDouble()
                    )
                    if (success) {
                        val profit = computeProfit(pos, candle.closePrice.toDouble())
                        capital += profit
                        logger.info("Closed position with profit={}, new capital={}", profit, capital)
                        openPosition = null
                    }
                }
            }
        }
    }

    private fun computeProfit(pos: OpenPosition, exitPrice: Double): Double {
        return when (pos.side) {
            "BUY" -> (exitPrice - pos.entryPrice) * pos.quantity
            "SELL" -> (pos.entryPrice - exitPrice) * pos.quantity
            else -> 0.0
        }
    }
}
