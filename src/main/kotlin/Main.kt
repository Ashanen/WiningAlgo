package main

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyEngine
import executor.RealTradeExecutor
import executor.SimulationTradeExecutor
import leverage.fetchHistoricalCandles
import model.Kline
import org.slf4j.LoggerFactory
import parser.CandleParser
import strategy.AlphaBollingerStrategy
import strategy.BollingerScalpingStrategy
import strategy.BreakoutStrategy
import strategy.MeanReversionStrategy

fun main() {
    val isBacktest = true

    if (isBacktest) {
        backtestMultipleStrategies()
    } else {
        realTrading()
    }
}

fun backtestModular() {
    val logger = LoggerFactory.getLogger("BacktestModular")

    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    val simulationExecutor = SimulationTradeExecutor()
    val strategy = AlphaBollingerStrategy()
    val engine = StrategyEngine(simulationExecutor, strategy)
    engine.capital = 1000.0

    // 2) Pobieramy dane z Binance
    val params = linkedMapOf<String, Any>(
        "symbol" to "BTCUSDT",
        "interval" to "15m",
        "limit" to 1000
    )
    val result = futuresClient.market().klines(params)

    // 3) Parsujemy JSON
    val historicalCandles = CandleParser.parseCandles(result)
    logger.info("Loaded {} historical candles", historicalCandles.size)

    // 4) Iterujemy
    for (candle in historicalCandles) {
        engine.processCandle(candle)
    }

    logger.info("Backtest done. Final capital = {}", engine.capital)
}

fun realTrading() {
    val logger = LoggerFactory.getLogger("RealTrading")

    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    val realExecutor = RealTradeExecutor(futuresClient)
    val strategy = AlphaBollingerStrategy()
    val engine = StrategyEngine(realExecutor, strategy)
    engine.capital = 1000.0

    // Tutaj możesz wczytać history i subskrybować streaming.
    // Albo np. co 15m pobierać najnowszą świecę z API i wywoływać:
    // engine.processCandle(latestCandle)
    // w pętli / w job

    logger.info("Starting real trading with capital={}", engine.capital)
    // ...
}

fun backtestMultipleStrategies() {
    val logger = LoggerFactory.getLogger("BacktestMulti")

    // Inicjuj client, parser
    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)

    val result = futuresClient.market().klines(
        linkedMapOf<String, Any>(
            "symbol" to "BTCUSDT",
            "interval" to "15m",
            "limit" to 1000
        )
    )
    val historicalCandles = CandleParser.parseCandles(result)
    logger.info("Loaded {} historical candles", historicalCandles.size)

    // Lista strategii
    val strategies = listOf(
        AlphaBollingerStrategy(),
        MeanReversionStrategy(),
        BreakoutStrategy(),
        BollingerScalpingStrategy()
    )

    for (strat in strategies) {
        val engine = StrategyEngine(SimulationTradeExecutor(), strat)
        engine.capital = 1000.0

        for (candle in historicalCandles) {
            engine.processCandle(candle)
        }

        logger.info("Strategy {} done. Final capital = {}", strat.name, engine.capital)
    }
}

fun backtestMultiInterval() {
    val logger = LoggerFactory.getLogger("BacktestMultiInterval")

    // 1) Inicjalizacja klienta
    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    // symulowany TradeExecutor
    val simulationExecutor = SimulationTradeExecutor()

    // 2) Lista strategii
    val strategies = listOf(
        AlphaBollingerStrategy(),
        MeanReversionStrategy(),
        BreakoutStrategy(),
        BollingerScalpingStrategy()
    )

    // 3) Lista interwałów
    val intervals = listOf("5m", "1h", "1d")

    // 4) Dla każdego interwału pobieramy dane i testujemy każdą strategię
    for (interval in intervals) {
        logger.info("=== Testing interval: {} ===", interval)
        val historicalCandles = fetchHistoricalCandles(futuresClient, "BTCUSDT", interval, 1000)
        logger.info("Loaded {} candles for interval={}", historicalCandles.size, interval)

        // Uruchamiamy backtesty
        for (strategy in strategies) {
            val engine = StrategyEngine(simulationExecutor, strategy)
            engine.capital = 1000.0

            // Symulacja
            for (candle in historicalCandles) {
                engine.processCandle(candle)
            }

            logger.info(
                "Strategy={} Interval={} -> Final capital={}",
                strategy.name,
                interval,
                engine.capital
            )
        }
    }
}

