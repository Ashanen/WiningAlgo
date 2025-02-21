package engine

import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import org.slf4j.LoggerFactory
import strategy.Strategy

class StrategyManager(
    private val strategies: List<Strategy>,
    private val tradeExecutor: TradeExecutor
) {
    private val logger = LoggerFactory.getLogger(StrategyManager::class.java)

    var capital: Double = 1000.0
    private var openPosition: OpenPosition? = null

    var totalTrades = 0
    var totalWins = 0
    var totalLosses = 0

    /**
     * Wywoływana dla każdej świecy.
     */
    fun onNewCandle(candle: Kline, candlesSoFar: List<Kline>) {
        if (openPosition != null) {
            // Mamy otwartą pozycję => sprawdzamy sygnały zamknięcia
            for (strategy in strategies) {
                val exitSignals = strategy.onUpdatePosition(candle, openPosition!!)
                for (sig in exitSignals) {
                    if (sig.type == SignalType.CLOSE) {
                        closePosition(sig.price)
                        return
                    }
                }
            }
        } else {
            // Nie mamy pozycji => sprawdzamy sygnały otwarcia
            for (strategy in strategies) {
                val signals = strategy.onNewCandle(candle, candlesSoFar, capital)
                for (sig in signals) {
                    if (sig.type == SignalType.BUY || sig.type == SignalType.SELL) {
                        openPosition(sig)
                        return
                    }
                }
            }
        }
    }

    private fun openPosition(signal: StrategySignal) {
        val sideName = signal.type.name // "BUY"/"SELL"
        val pos = OpenPosition(
            side = sideName,
            entryPrice = signal.price,
            stopLoss = signal.stopLoss,
            takeProfit = signal.takeProfit,
            quantity = signal.quantity,
            maxFavorable = signal.price,
            minFavorable = signal.price
        )
        openPosition = pos
        logger.info("Opened position: {}, capital={}", pos, capital)
        tradeExecutor.openTrade(sideName, pos.quantity, pos.entryPrice, pos.stopLoss, pos.takeProfit)
    }

    private fun closePosition(exitPrice: Double) {
        val pos = openPosition ?: return
        val profit = if (pos.side == "BUY") {
            (exitPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - exitPrice) * pos.quantity
        }
        capital += profit

        totalTrades++
        if (profit >= 0) totalWins++ else totalLosses++

        logger.info("Closed position with profit={}, new capital={}", profit, capital)
        tradeExecutor.closeTrade(pos.side, pos.quantity, exitPrice)
        openPosition = null
    }

    /**
     * Metoda pomocnicza do wyciągania logów/raportu – np. do zapisu w pliku.
     */
    fun extraLog(): String {
        val sb = StringBuilder()
        sb.append("Final capital: $capital\n")
        sb.append("Total trades: $totalTrades\n")
        sb.append("Wins: $totalWins\n")
        sb.append("Losses: $totalLosses\n")
        val winRate = if (totalTrades > 0) (totalWins.toDouble() / totalTrades) * 100.0 else 0.0
        sb.append("Win rate: %.2f%%\n".format(winRate))
        return sb.toString()
    }
}
