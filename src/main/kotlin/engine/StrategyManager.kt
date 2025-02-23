package engine

import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import model.TradeRecord
import executor.TradeExecutor
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

    val tradeRecords = mutableListOf<TradeRecord>()

    fun onNewCandle(candle: Kline, candles: List<Kline>) {
        if (openPosition != null) {
            for (strategy in strategies) {
                val exitSignals = strategy.onUpdatePosition(candle, openPosition!!)
                for (sig in exitSignals) {
                    if (sig.type == SignalType.CLOSE) {
                        closePosition(candle, sig.price)
                        return
                    }
                }
            }
        } else {
            for (strategy in strategies) {
                val signals = strategy.onNewCandle(candle, candles, capital)
                for (sig in signals) {
                    if (sig.type == SignalType.BUY || sig.type == SignalType.SELL) {
                        openPosition(candle, sig, strategy.name)
                        return
                    }
                }
            }
        }
    }

    private fun openPosition(candle: Kline, signal: StrategySignal, strategyName: String) {
        val pos = OpenPosition(
            side = signal.type.name,
            entryPrice = signal.price,
            stopLoss = signal.stopLoss,
            takeProfit = signal.takeProfit,
            quantity = signal.quantity,
            maxFavorable = signal.price,
            minFavorable = signal.price,
            openTime = candle.openTime,
            strategyName = strategyName,
            indicatorData = signal.indicatorData
        )
        openPosition = pos
        logger.info("Opened position: $pos, capital=$capital, indicatorData=${signal.indicatorData}")
        tradeExecutor.openTrade(pos.side, pos.quantity, pos.entryPrice, pos.stopLoss, pos.takeProfit)
    }

    private fun closePosition(candle: Kline, exitPrice: Double) {
        val pos = openPosition ?: return
        val profit = if (pos.side == "BUY") {
            (exitPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - exitPrice) * pos.quantity
        }
        capital += profit

        totalTrades++
        if (profit >= 0) totalWins++ else totalLosses++

        logger.info("Closed position with profit=$profit, new capital=$capital")
        tradeExecutor.closeTrade(pos.side, pos.quantity, exitPrice)

        val exitTime = candle.openTime
        val durationMillis = exitTime - pos.openTime
        val tradeRecord = TradeRecord(
            strategyName = pos.strategyName,
            entryTime = pos.openTime,
            exitTime = exitTime,
            entryPrice = pos.entryPrice,
            exitPrice = exitPrice,
            profit = profit,
            durationMillis = durationMillis,
            indicatorData = pos.indicatorData
        )
        tradeRecords.add(tradeRecord)

        openPosition = null
    }

    fun extraLog(): String {
        val sb = StringBuilder()
        sb.append("Final capital: $capital\n")
        sb.append("Total trades: $totalTrades\n")
        sb.append("Wins: $totalWins\n")
        sb.append("Losses: $totalLosses\n")
        val winRate = if (totalTrades > 0) (totalWins.toDouble() / totalTrades) * 100.0 else 0.0
        sb.append("Win rate: %.2f%%\n".format(winRate))

        val tradesByStrategy = tradeRecords.groupBy { it.strategyName }
        for ((strategyName, trades) in tradesByStrategy) {
            sb.append("\nStrategy: $strategyName\n")
            sb.append("Number of trades: ${trades.size}\n")
            val avgProfit = trades.map { it.profit }.average()
            val bestTrade = trades.maxByOrNull { it.profit }?.profit ?: 0.0
            val worstTrade = trades.minByOrNull { it.profit }?.profit ?: 0.0
            val avgDurationMillis = trades.map { it.durationMillis }.average()
            sb.append("Average profit: %.2f\n".format(avgProfit))
            sb.append("Best trade profit: %.2f\n".format(bestTrade))
            sb.append("Worst trade profit: %.2f\n".format(worstTrade))
            sb.append("Average duration: %.2f seconds\n".format(avgDurationMillis / 1000.0))
            trades.forEach { trade ->
                if (trade.indicatorData != null) {
                    sb.append("Trade at ${trade.entryTime}: indicatorData = ${trade.indicatorData}\n")
                }
            }
        }

        return sb.toString()
    }
}
