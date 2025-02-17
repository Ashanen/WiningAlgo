package backtest

import API_KEY
import SECRET_KEY
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import engine.StrategyManager
import executor.SimulationTradeExecutor
import model.Kline
import org.slf4j.LoggerFactory
import parser.CandleParser
import strategy.BollingerScalpTrendStrategy
import strategy.RSIOverboughtOversoldTrendStrategy

/**
 * Wersja do testu wielu strategii, z jedną otwartą pozycją naraz.
 */
fun backtestMultipleStrategiesOnePosition() {
    val logger = LoggerFactory.getLogger("BacktestMulti")

    // 1) Pobieramy świece (tak jak robisz to obecnie):
    val futuresClient = UMFuturesClientImpl(API_KEY, SECRET_KEY)
    val interval = "15m"
    val result = futuresClient.market().klines(
        linkedMapOf<String, Any>(
            "symbol" to "BTCUSDT",
            "interval" to interval,
            "limit" to 1000
        )
    )
    val historicalCandles: List<Kline> = CandleParser.parseCandles(result)
    logger.info("Loaded {} historical candles", historicalCandles.size)

    // 2) Tworzymy listę strategii, np. Bollinger + RSI
    val strategies = listOf(
        BollingerScalpTrendStrategy(),
        RSIOverboughtOversoldTrendStrategy()
        // Dodaj tu, co chcesz
    )

    // 3) Tworzymy TradeExecutor do symulacji + StrategyManager
    val simulationExecutor = SimulationTradeExecutor()
    val manager = StrategyManager(strategies, simulationExecutor)
    manager.capital = 1000.0

    // 4) Iterujemy po świecach, wywołując manager.onNewCandle
    for ((i, candle) in historicalCandles.withIndex()) {
        val slice = historicalCandles.take(i + 1)
        manager.onNewCandle(candle, slice)
    }

    logger.info("Multi-strategy done. Final capital = {}", manager.capital)
}
