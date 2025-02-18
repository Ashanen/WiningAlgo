package leverage

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import model.Kline
import parser.CandleParser
import java.util.LinkedHashMap

fun setLeverage(futuresClient: UMFuturesClientImpl, symbol: String, leverage: Int): Boolean {
    val params = LinkedHashMap<String, Any>().apply {
        put("symbol", symbol)
        put("leverage", leverage)
    }
    return try {
        // For Binance Futures, use the appropriate method; for example, changeInitialLeverage
        val result = futuresClient.account().changeInitialLeverage(params)
        println("Leverage changed successfully: $result")
        true
    } catch (e: Exception) {
        println("Error changing leverage: ${e.message}")
        false
    }
}

fun fetchHistoricalCandles(
    futuresClient: UMFuturesClientImpl,
    symbol: String,
    interval: String,
    limit: Int = 1000
): List<Kline> {
    val params = linkedMapOf<String, Any>(
        "symbol" to symbol,
        "interval" to interval,
        "limit" to limit
    )
    val result = futuresClient.market().klines(params)
    return CandleParser.parseCandles(result)
}

fun getLatestCandle(
    futuresClient: UMFuturesClientImpl,
    symbol: String,
    interval: String
): Kline? {
    val params = linkedMapOf<String, Any>(
        "symbol" to symbol,
        "interval" to interval,
        "limit" to 1
    )
    return try {
        val result = futuresClient.market().klines(params)
        val candles = parser.CandleParser.parseCandles(result)
        if (candles.isNotEmpty()) candles.last() else null
    } catch (e: Exception) {
        println("Error fetching latest candle: ${e.message}")
        null
    }
}

