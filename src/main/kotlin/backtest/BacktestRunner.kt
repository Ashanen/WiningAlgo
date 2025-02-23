package backtest

import API_KEY
import historical.HistoricalDataManager
import model.Kline
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyManager
import executor.SimulationTradeExecutor
import org.slf4j.LoggerFactory
import strategy.BollingerScalpingStrategy
import strategy.EnhancedAdaptiveMACDStrategy
import strategy.RSIOverboughtOversoldTrendStrategy

object BacktestRunner {
    private val logger = LoggerFactory.getLogger(BacktestRunner::class.java)

    fun runBacktest(rangeYears: Int = 2, symbol: String = "BTCUSDT", interval: String = "15m") {
        // Inicjalizacja klienta – upewnij się, że API_KEY i SECRET_KEY są poprawnie zdefiniowane
        val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
        val historicalManager = HistoricalDataManager(futuresClient, symbol, interval)
        historicalManager.ensureYearlyDataUpToDate(rangeYears)
        val allKlines: List<Kline> = historicalManager.loadAllYearData(rangeYears)
        logger.info("Loaded total ${allKlines.size} klines for backtest")

        val strategies = listOf(
            RSIOverboughtOversoldTrendStrategy(),
            EnhancedAdaptiveMACDStrategy(),
            BollingerScalpingStrategy()
        )

        val simulationExecutor = SimulationTradeExecutor()
        val manager = StrategyManager(strategies, simulationExecutor).apply {
            capital = 1000.0
        }

        for ((i, candle) in allKlines.withIndex()) {
            val slice = allKlines.take(i + 1)
            manager.onNewCandle(candle, slice)
        }

        logger.info("Backtest complete. Final capital = ${manager.capital}")
        logger.info("Total Trades = ${manager.totalTrades}, Wins = ${manager.totalWins}, Losses = ${manager.totalLosses}")

        // Wydruk raportu na konsolę
        println("=== BACKTEST REPORT ===")
        println(manager.extraLog())

        // Zapis raportu do pliku
        BacktestFileLogger.writeReport(manager, extraLog = "")
    }

    // Funkcja zgodna z Main.kt
    fun backtestMultipleStrategiesOnePosition(rangeYears: Int = 2, symbol: String = "BTCUSDT", interval: String = "15m") {
        runBacktest(rangeYears, symbol, interval)
    }
}
