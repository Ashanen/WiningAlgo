package backtest

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyManager
import executor.SimulationTradeExecutor
import historical.HistoricalDataManagerYearly
import model.Kline
import org.slf4j.LoggerFactory
import strategy.BollingerScalpingStrategy
import strategy.MovingAverageCrossStrategy

object BacktestRunner {
    private val logger = LoggerFactory.getLogger(BacktestRunner::class.java)

    /**
     * Backtest z wykorzystaniem wielu strategii (ale tylko jedna otwarta pozycja) na danych z ostatnich 2 lat.
     */
    fun backtestMultipleStrategiesOnePosition(rangeYears: Int = 2) {
        val symbol = "BTCUSDT"
        val interval = "15m"

        val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
        val historicalManager = HistoricalDataManagerYearly(
            futuresClient,
            symbol = symbol,
            interval = interval
        )

        // 1) Upewnij się, że mamy dane roczne z ostatnich 2 lat
        historicalManager.ensureYearlyDataUpToDate(rangeYears)

        // 2) Ładujemy wszystkie roczne pliki
        val allKlines: List<Kline> = historicalManager.loadAllYearData(rangeYears)
        logger.info("Loaded total ${allKlines.size} klines for backtest")

        // 3) Tworzymy listę strategii – obie strategie będą rozpatrywane, ale otwarta może być tylko jedna pozycja
        val strategies = listOf(
            BollingerScalpingStrategy(),
            MovingAverageCrossStrategy()
        )

        val simulationExecutor = SimulationTradeExecutor()
        val manager = StrategyManager(strategies, simulationExecutor).apply {
            capital = 1000.0
        }

        // 4) Backtest – przetwarzamy kolejne świece
        for ((i, candle) in allKlines.withIndex()) {
            val slice = allKlines.take(i + 1)
            manager.onNewCandle(candle, slice)
        }

        logger.info("Multi-strategy done. Final capital = ${manager.capital}")
        logger.info("Trades=${manager.totalTrades}, Wins=${manager.totalWins}, Losses=${manager.totalLosses}")

        // 5) Zapis raportu do pliku
        BacktestFileLogger.writeReport(manager, extraLog = "rangeYears=$rangeYears, symbol=$symbol, interval=$interval")
    }
}
