import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.WebSocketCallback

import engine.StrategyEngine
import strategy.AlphaBollingerStrategy
import executor.RealTradeExecutor
import executor.SimulationTradeExecutor
import org.slf4j.LoggerFactory
import parser.CandleParser

fun main() {
    backtestModular()
}

fun backtestModular() {
    val logger = LoggerFactory.getLogger("BacktestModular")
    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    val simulationExecutor = SimulationTradeExecutor()
    val strategy = AlphaBollingerStrategy()
    val engine = StrategyEngine(simulationExecutor, strategy)
    engine.capital = 1000.0

    val params = linkedMapOf<String, Any>(
        "symbol" to "BTCUSDT",
        "interval" to "15m",
        "limit" to "1000"
    )
    val result = futuresClient.market().klines(params)
    val historicalCandles = CandleParser.parseCandles(result)
    logger.info("Loaded {} historical candles", historicalCandles.size)

    for (candle in historicalCandles) {
        engine.processCandle(candle)
    }

    logger.info("Backtest done. Final capital = {}", engine.capital)
}

fun liveModular() {
    val logger = LoggerFactory.getLogger("LiveModular")
    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    val websocketClient = UMWebsocketClientImpl()
    val realExecutor = RealTradeExecutor(futuresClient)
    val strategy = AlphaBollingerStrategy()
    val engine = StrategyEngine(realExecutor, strategy)
    engine.capital = 1000.0

    // Wczytanie historycznych świec
    val params = linkedMapOf<String, Any>(
        "symbol" to "BTCUSDT",
        "interval" to "15m",
        "limit" to "1000"
    )
    val result = futuresClient.market().klines(params)
    val historicalCandles = CandleParser.parseCandles(result)
    engine.candles.addAll(historicalCandles)

    // Subskrypcja websocket
    val callback = WebSocketCallback { response ->
        try {
            val newCandles = CandleParser.parseCandles("[$response]")
            if (newCandles.isNotEmpty()) {
                val newCandle = newCandles[0]
                if (newCandle.isClosed) {
                    engine.processCandle(newCandle)
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing live data: {}", e.message)
        }
    }
    websocketClient.klineStream("btcusdt", "5m", callback)

    logger.info("Subscribed to live candle stream. Starting loop...")
    while (true) {
        Thread.sleep(1000)
    }
}
