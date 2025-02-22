package engine

import executor.TradeExecutor
import model.Kline
import model.OpenPosition
import model.SignalType
import model.StrategySignal
import model.TradeRecord
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

    // Lista rejestrów handlu – pozwoli analizować skuteczność poszczególnych strategii.
    val tradeRecords = mutableListOf<TradeRecord>()

    /**
     * Wywoływana dla każdej świecy – przy otwarciu pozycji przekazujemy również nazwę strategii.
     */
    fun onNewCandle(candle: Kline, candlesSoFar: List<Kline>) {
        if (openPosition != null) {
            // Sprawdzamy sygnały zamknięcia, gdy mamy otwartą pozycję.
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
            // Brak otwartej pozycji – sprawdzamy sygnały otwarcia.
            for (strategy in strategies) {
                val signals = strategy.onNewCandle(candle, candlesSoFar, capital)
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
            side = signal.type.name, // "BUY" lub "SELL"
            entryPrice = signal.price,
            stopLoss = signal.stopLoss,
            takeProfit = signal.takeProfit,
            quantity = signal.quantity,
            maxFavorable = signal.price,
            minFavorable = signal.price,
            openTime = candle.openTime,  // zapamiętujemy czas otwarcia pozycji
            strategyName = strategyName,
            indicatorData = signal.indicatorData // przekazujemy dodatkowe dane wskaźnikowe
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

        // Rejestrujemy szczegółowy wpis handlu
        val exitTime = candle.openTime // lub candle.closeTime, zależnie od preferencji
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

    /**
     * Generuje raport końcowy z dodatkowymi danymi:
     * - Statystyki ogólne (kapitał, liczba handli, winrate)
     * - Podsumowanie wg strategii: liczba handli, średni zysk, najlepszy, najgorszy trade oraz średni czas trwania.
     * - Wyświetla również dane wskaźnikowe, jeśli były dostępne.
     */
    fun extraLog(): String {
        val sb = StringBuilder()
        sb.append("Final capital: $capital\n")
        sb.append("Total trades: $totalTrades\n")
        sb.append("Wins: $totalWins\n")
        sb.append("Losses: $totalLosses\n")
        val winRate = if (totalTrades > 0) (totalWins.toDouble() / totalTrades) * 100.0 else 0.0
        sb.append("Win rate: %.2f%%\n".format(winRate))

        // Podsumowanie handli wg strategii
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

            // Opcjonalnie: logowanie danych wskaźnikowych, jeśli dostępne
            trades.forEach { trade ->
                if (trade.indicatorData != null) {
                    sb.append("Trade at ${trade.entryTime}: indicatorData = ${trade.indicatorData}\n")
                }
            }
        }

        return sb.toString()
    }
}
