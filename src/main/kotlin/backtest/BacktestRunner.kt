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
import strategy.Strategy

object BacktestRunner {
    private val logger = LoggerFactory.getLogger(BacktestRunner::class.java)

    /**
     * Przykład backtestu, w którym:
     *  1. Pobieramy/uzupełniamy dane roczne (rangeYears).
     *  2. Ładujemy pliki roczne i łączymy w jedną listę Kline.
     *  3. Uruchamiamy StrategyManager z wybranymi strategiami.
     *  4. Logujemy wyniki do pliku i do konsoli.
     */
    fun backtestMultipleStrategiesOnePosition(rangeYears: Int = 6) {
        val symbol = "BTCUSDT"
        val interval = "15m"

        val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
        val historicalManager = HistoricalDataManagerYearly(
            futuresClient,
            symbol = symbol,
            interval = interval
        )

        // 1) Upewniamy się, że mamy dane roczne z ostatnich N lat
        historicalManager.ensureYearlyDataUpToDate(rangeYears)

        // 2) Ładujemy wszystkie roczne pliki
        val allKlines: List<Kline> = historicalManager.loadAllYearData(rangeYears)
        logger.info("Loaded total ${allKlines.size} klines for backtest")

        // 3) Tworzymy listę strategii (np. tylko BollingerScalpingStrategy)
        val strategies: List<Strategy> = listOf(
            BollingerScalpingStrategy()
        )

        val simulationExecutor = SimulationTradeExecutor()
        val manager = StrategyManager(strategies, simulationExecutor).apply {
            capital = 1000.0
        }

        // 4) Odpalamy backtest
        for ((i, candle) in allKlines.withIndex()) {
            val slice = allKlines.take(i + 1)
            manager.onNewCandle(candle, slice)
        }

        logger.info("Multi-strategy done. Final capital = ${manager.capital}")
        logger.info("Trades=${manager.totalTrades}, Wins=${manager.totalWins}, Losses=${manager.totalLosses}")

        // 5) Zapis do pliku
        BacktestFileLogger.writeReport(manager, extraLog = "rangeYears=$rangeYears, symbol=$symbol, interval=$interval")
    }
}
