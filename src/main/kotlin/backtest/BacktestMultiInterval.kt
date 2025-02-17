package backtest

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyEngine
import executor.SimulationTradeExecutor
import leverage.fetchHistoricalCandles
import org.slf4j.LoggerFactory
import strategy.AlphaBollingerStrategy
import strategy.BollingerScalpingStrategy
import strategy.BreakoutStrategy
import strategy.MeanReversionStrategy

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