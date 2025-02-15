package leverage

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
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