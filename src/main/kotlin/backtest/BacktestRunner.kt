package backtest

import API_KEY
import SECRET_KEY
import analyzer.LiquidityAnalyzer
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyManager
import executor.SimulationTradeExecutor
import historical.HistoricalDataManager
import model.Kline
import org.slf4j.LoggerFactory
import strategy.BollingerScalpingStrategy
import strategy.EnhancedAdaptiveMACDStrategy
import strategy.RSIOverboughtOversoldTrendStrategy

object BacktestRunner {
    private val logger = LoggerFactory.getLogger(BacktestRunner::class.java)

    fun runBacktest(rangeYears: Int = 2, symbol: String = "BTCUSDT", interval: String = "15m") {
        val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
        val historicalManager = HistoricalDataManager(futuresClient, symbol, interval)
        historicalManager.ensureYearlyDataUpToDate(rangeYears)
        val allKlines: List<Kline> = historicalManager.loadAllYearData(rangeYears)
        logger.info("Loaded total ${allKlines.size} klines for backtest on interval $interval")

        val strategies = listOf(
            EnhancedAdaptiveMACDStrategy(fastPeriod = 12, slowPeriod = 26, signalPeriod = 9),
            RSIOverboughtOversoldTrendStrategy(overbought = 80, oversold = 20),
            BollingerScalpingStrategy(bbPeriod = 20)
        )

        val simulationExecutor = SimulationTradeExecutor()
        val manager = StrategyManager(strategies, simulationExecutor).apply {
            capital = 1000.0
        }

        val liquidityAnalyzer = LiquidityAnalyzer()

        for ((i, candle) in allKlines.withIndex()) {
            liquidityAnalyzer.processCandle(candle)
            val slice = allKlines.take(i + 1)
            manager.onNewCandle(candle, slice)
        }

        logger.info("Backtest complete. Final capital = ${manager.capital}")
        logger.info("Total Trades = ${manager.totalTrades}, Wins = ${manager.totalWins}, Losses = ${manager.totalLosses}")

        val extraLog = "rangeYears=$rangeYears, symbol=$symbol, interval=$interval\n" +
                liquidityAnalyzer.generateReport()

        println("=== BACKTEST REPORT ===")
        println(manager.extraLog())
        println(extraLog)

        BacktestFileLogger.writeReport(manager, extraLog = extraLog)
    }
}