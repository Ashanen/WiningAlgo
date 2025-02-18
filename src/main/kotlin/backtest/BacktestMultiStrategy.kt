package backtest

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import config.TradingConfig
import engine.StrategyManager
import executor.SimulationTradeExecutor
import model.Kline
import org.slf4j.LoggerFactory
import parser.CandleParser.parseCandles
import strategy.BollingerScalpTrendStrategy
import strategy.RSIOverboughtOversoldTrendStrategy

fun backtestMultipleStrategiesOnePosition() {
    val logger = LoggerFactory.getLogger("BacktestMulti")

    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)

    // Use the same config for symbol, interval, limit
    val restParams = linkedMapOf<String, Any>(
        "symbol" to TradingConfig.SYMBOL,
        "interval" to TradingConfig.INTERVAL,
        "limit" to TradingConfig.LIMIT
    )
    val result = futuresClient.market().klines(restParams)
    val historicalCandles: List<Kline> = parseCandles(result)
    logger.info("Loaded {} historical candles for backtest", historicalCandles.size)

    val strategies = listOf(
        BollingerScalpTrendStrategy(),
        RSIOverboughtOversoldTrendStrategy()
    )

    val simulationExecutor = SimulationTradeExecutor()
    val manager = StrategyManager(strategies, simulationExecutor).apply {
        capital = 1000.0
    }

    for ((i, candle) in historicalCandles.withIndex()) {
        val slice = historicalCandles.take(i + 1)
        manager.onNewCandle(candle, slice)
    }

    logger.info("Multi-strategy done. Final capital = {}", manager.capital)
}

