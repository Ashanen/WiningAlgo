import backtest.backtestMultipleStrategiesOnePosition
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyEngine
import executor.RealTradeExecutor
import org.slf4j.LoggerFactory
import strategy.*

fun main() {
    val isBacktest = true

    if (isBacktest) {
        backtestMultipleStrategiesOnePosition()
    } else {
        realTrading()
    }
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




