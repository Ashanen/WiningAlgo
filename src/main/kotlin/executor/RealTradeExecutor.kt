package executor

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.LinkedHashMap

class RealTradeExecutor(
    private val futuresClient: UMFuturesClientImpl
) : TradeExecutor {

    private val logger = LoggerFactory.getLogger(RealTradeExecutor::class.java)
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override fun openTrade(
        side: String,
        quantity: Double,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double
    ): Boolean {
        val allowedPrecision = 3
        val formattedQuantity = BigDecimal(quantity)
            .setScale(allowedPrecision, RoundingMode.DOWN)
            .toPlainString()

        val currentTime = System.currentTimeMillis()
        val offset = getServerTimeOffset()
        val adjustedTimestamp = currentTime - 10000 - offset

        val parameters = LinkedHashMap<String, Any>().apply {
            put("symbol", "BTCUSDT")
            put("side", side)
            put("type", "MARKET")
            put("quantity", formattedQuantity)
            put("timestamp", adjustedTimestamp)
            put("recvWindow", 6000)
        }

        return try {
            val result = futuresClient.account().newOrder(parameters)
            logger.info("Real Executor: Order {} placed. Result: {}", side, result)
            true
        } catch (e: Exception) {
            logger.error("Real Executor: Error placing order {}: {}", side, e.message)
            false
        }
    }

    override fun closeTrade(openSide: String, quantity: Double, exitPrice: Double): Boolean {
        val closeSide = if (openSide == "BUY") "SELL" else "BUY"

        val allowedPrecision = 3
        val formattedQuantity = BigDecimal(quantity)
            .setScale(allowedPrecision, RoundingMode.DOWN)
            .toPlainString()

        val currentTime = System.currentTimeMillis()
        val offset = getServerTimeOffset()
        val adjustedTimestamp = currentTime - 10000 - offset

        val parameters = LinkedHashMap<String, Any>().apply {
            put("symbol", "BTCUSDT")
            put("side", closeSide)
            put("type", "MARKET")
            put("leverage", 3)
            put("quantity", formattedQuantity)
            put("timestamp", adjustedTimestamp)
            put("recvWindow", 6000)
        }

        return try {
            val result = futuresClient.account().newOrder(parameters)
            logger.info("Real Executor: Close order {} placed. Result: {}", closeSide, result)
            true
        } catch (e: Exception) {
            logger.error("Real Executor: Error placing close order {}: {}", closeSide, e.message)
            false
        }
    }

    private fun getServerTimeOffset(): Long {
        val url = "https://fapi.binance.com/fapi/v1/time"
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.error("Real Executor: Error fetching server time. Code: ${response.code}")
                    return 0L
                }
                val body = response.body?.string() ?: return 0L
                val jsonElement = json.parseToJsonElement(body)
                val serverTime = jsonElement.jsonObject["serverTime"]?.jsonPrimitive?.content?.toLong() ?: 0L
                val offset = System.currentTimeMillis() - serverTime
                logger.info("Real Executor: Server time offset: {} ms", offset)
                offset
            }
        } catch (e: Exception) {
            logger.error("Real Executor: Error fetching server time: {}", e.message)
            0L
        }
    }
}
