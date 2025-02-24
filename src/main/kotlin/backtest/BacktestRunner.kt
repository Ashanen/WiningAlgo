package backtest

import API_KEY
import SECRET_KEY
import analyzer.LiquidityAnalyzer
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import config.StrategyParameters
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

        val simulationExecutor = SimulationTradeExecutor()
        val manager = StrategyManager(strategies, simulationExecutor).apply {
            capital = 1000.0
        }

        val liquidityAnalyzer = LiquidityAnalyzer()

        for ((i, candle) in allKlines.withIndex()) {
            liquidityAnalyzer.processCandle(candle)
            val slice = allKlines.take(i + 1)
            manager.onNewCandle(candle, slice)
            if (i % 1000 == 0) {
                logger.info("Processed $i candles")
            }
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

val macdParams = StrategyParameters.EnhancedAdaptiveMACDParams(
    fastPeriod = 12,
    slowPeriod = 26,
    signalPeriod = 9,
    rsiPeriod = 14,
    atrPeriod = 14,
    useAdaptive = true,
    useStochastic = true,
    stochasticPeriod = 14,
    stochasticDPeriod = 3,
    stochasticOverbought = 80.0,
    stochasticOversold = 20.0,
    useAdx = true,
    adxPeriod = 14,
    adxThreshold = 25.0,
    useIchimoku = true,
    tenkanPeriod = 9,
    kijunPeriod = 26,
    senkouSpanBPeriod = 52,
    ichimokuDisplacement = 26,
    useParabolicSar = true,
    baseRiskPercent = 0.01,
    atrMultiplierSL = 2.0,
    atrMultiplierTP = 3.0
)

val rsiParams = StrategyParameters.RSIOverboughtOversoldParams(
    rsiPeriod = 14,
    overbought = 80,
    oversold = 20,
    emaPeriod = 50,
    atrPeriod = 14,
    baseRiskPercent = 0.01,
    atrMultiplierSL = 2.0,
    atrMultiplierTP = 3.0
)

val bollingerParams = StrategyParameters.BollingerScalpingParams(
    bbPeriod = 20,
    bbNumDevs = 2.0,
    emaPeriod = 50,
    atrPeriod = 14,
    baseRiskPercent = 0.01,
    atrMultiplierSL = 1.0,
    atrMultiplierTP = 3.0
)

val strategies = listOf(
    EnhancedAdaptiveMACDStrategy(macdParams),
    RSIOverboughtOversoldTrendStrategy(rsiParams),
    BollingerScalpingStrategy(bollingerParams)
)