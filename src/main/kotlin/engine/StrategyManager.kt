package engine

import analyzer.LiquidityStats
import executor.TradeExecutor
import model.*
import org.slf4j.LoggerFactory
import strategy.Strategy

class StrategyManager(
    private val strategies: List<Strategy>,
    private val tradeExecutor: TradeExecutor
) {
    private val logger = LoggerFactory.getLogger(StrategyManager::class.java)

    var capital: Double = 1000.0
    private val openPositions = mutableMapOf<String, MutableList<OpenPosition>>()

    var totalTrades = 0
    var totalWins = 0
    var totalLosses = 0

    val tradeRecords = mutableListOf<TradeRecord>()

    fun onNewCandle(candle: Kline, candles: List<Kline>) {
        for (strategy in strategies) {
            val strategyPositions = openPositions.getOrPut(strategy.name) { mutableListOf() }
            val positionsToClose = mutableListOf<OpenPosition>()
            for (position in strategyPositions) {
                val signals = strategy.onUpdatePosition(candle, candles, position)
                if (signals.any { it.type == SignalType.CLOSE }) {
                    positionsToClose.add(position)
                }
            }
            for (position in positionsToClose) {
                closePosition(candle, position, strategy.name)
            }

            if (strategyPositions.isEmpty()) {
                val signals = strategy.onNewCandle(candle, candles, capital)
                for (sig in signals) {
                    if (sig.type == SignalType.BUY || sig.type == SignalType.SELL) {
                        openPosition(candle, sig, strategy.name)
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
        openPositions.getOrPut(strategyName) { mutableListOf() }.add(pos)
        logger.info("Opened position: $pos, capital=$capital, indicatorData=${signal.indicatorData}")
        tradeExecutor.openTrade(pos.side, pos.quantity, pos.entryPrice, pos.stopLoss, pos.takeProfit)
    }

    private fun closePosition(candle: Kline, position: OpenPosition, strategyName: String) {
        val currentPrice = candle.closePrice.toDoubleOrNull() ?: position.entryPrice
        val profit = if (position.side == "BUY") {
            (currentPrice - position.entryPrice) * position.quantity
        } else {
            (position.entryPrice - currentPrice) * position.quantity
        }
        capital += profit

        totalTrades++
        if (profit >= 0) totalWins++ else totalLosses++

        logger.info("Closed position from $strategyName with profit=$profit, new capital=$capital")
        tradeExecutor.closeTrade(position.side, position.quantity, currentPrice)

        val durationMillis = candle.openTime - position.openTime
        val tradeRecord = TradeRecord(
            strategyName = strategyName,
            entryTime = position.openTime,
            exitTime = candle.openTime,
            entryPrice = position.entryPrice,
            exitPrice = currentPrice,
            profit = profit,
            durationMillis = durationMillis,
            indicatorData = position.indicatorData,
            volume = candle.volume.toDoubleOrNull() ?: 0.0,
            liquidityStats = LiquidityStats()
        )
        tradeRecords.add(tradeRecord)
        openPositions[strategyName]?.remove(position)
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