package live

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.WebSocketCallback
import config.TradingConfig
import executor.RealTradeExecutor
import model.Kline
import org.slf4j.LoggerFactory
import parser.CandleParser.parseCandles
import strategy.AlphaBollingerStrategy // or whichever you want
import engine.StrategyEngine // or StrategyManager
import java.lang.Thread.sleep

class LiveTradingManager{
    private val logger = LoggerFactory.getLogger(LiveTradingManager::class.java)

    private val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    private val websocketClient = UMWebsocketClientImpl()

    // Optional: set your leverage if desired
    private fun setLeverage(symbol: String, leverage: Int) {
        try {
            val params = linkedMapOf<String, Any>(
                "symbol" to symbol,
                "leverage" to leverage
            )
            val result = futuresClient.account().changeInitialLeverage(params)
            logger.info("Leverage changed: $result")
        } catch (e: Exception) {
            logger.error("Error changing leverage: {}", e.message)
        }
    }

    fun runLive() {
        logger.info("Loading historical candles for ${TradingConfig.SYMBOL} (${TradingConfig.INTERVAL}), limit=${TradingConfig.LIMIT}...")

        // 1) Load historical candles via REST
        val restParams = linkedMapOf<String, Any>(
            "symbol" to TradingConfig.SYMBOL,
            "interval" to TradingConfig.INTERVAL,
            "limit" to TradingConfig.LIMIT
        )
        val restResult = futuresClient.market().klines(restParams)
        val historicalCandles: List<Kline> = parseCandles(restResult)
        logger.info("Loaded {} historical candles for live trading.", historicalCandles.size)

        // 2) Optionally set your leverage
        setLeverage(TradingConfig.SYMBOL, 5)

        // 3) Create RealTradeExecutor + Strategy
        val realExecutor = RealTradeExecutor(futuresClient)
        val strategy = AlphaBollingerStrategy()
        val engine = StrategyEngine(realExecutor, strategy).apply {
            capital = 500.0  // or 1000.0, up to you
        }

        // Feed historical candles so the strategy has context
        for (candle in historicalCandles) {
            if (candle.isClosed) {
                engine.processCandle(candle)
            }
        }

        logger.info("Subscribing to WebSocket for kline stream: ${TradingConfig.SYMBOL}, interval=${TradingConfig.INTERVAL}")

        // 4) WebSocket callback
        val callback = WebSocketCallback { jsonLine ->
            try {
                // parseCandles expects an array
                val newCandles = parseCandles("[$jsonLine]")
                if (newCandles.isNotEmpty()) {
                    val newCandle = newCandles[0]
                    // Only act on a fully closed candle
                    if (newCandle.isClosed) {
                        logger.info("Received closed candle: {}", jsonLine)
                        engine.processCandle(newCandle)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing live data: {}", e.message)
            }
        }

        // Subscribe
        val streamId = websocketClient.klineStream(
            TradingConfig.SYMBOL.lowercase(),
            TradingConfig.INTERVAL,
            callback
        )
        logger.info("WebSocket subscribed. streamId={}", streamId)

        // 5) Keep running
        logger.info("Live trading loop started. Ctrl-C to exit.")
        while (true) {
            sleep(1000)
        }
    }
}
