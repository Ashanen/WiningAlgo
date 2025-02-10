import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.WebSocketCallback
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.lang.Thread.sleep
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.max

// Model danych świecy, zgodny z danymi Binance
@Serializable
data class KlineData(
    @SerialName("e") val eventType: String,
    @SerialName("E") val eventTime: Long,
    @SerialName("s") val symbol: String,
    @SerialName("k") val kline: Kline
)

@Serializable
data class Kline(
    @SerialName("t") val openTime: Long,
    @SerialName("T") val closeTime: Long,
    @SerialName("o") val openPrice: String,
    @SerialName("h") val highPrice: String,
    @SerialName("l") val lowPrice: String,
    @SerialName("c") val closePrice: String,
    @SerialName("x") val isClosed: Boolean
)

// Prosty model pivotu
data class Pivot(val time: Long, val level: Double)

fun main() {
    // Wprowadź swoje klucze API poniżej (dla symulacji nie są one używane do faktycznych zleceń)
    val strategy = LivermoreStrategy(API_KEY, SECRET_KEY, simulationMode = true)
    strategy.start()
}

class LivermoreStrategy(apiKey: String, secretKey: String, private val simulationMode: Boolean) {

    private val logger = LoggerFactory.getLogger(LivermoreStrategy::class.java)
    private val restClient = UMFuturesClientImpl(apiKey, secretKey)
    private val websocketClient = UMWebsocketClientImpl()
    private val json = Json { ignoreUnknownKeys = true }

    // Lista świec – zachowujemy ostatnie 100 świec
    private val candles = mutableListOf<Kline>()
    // Listy wykrytych pivot points
    private val supplyPivots = mutableListOf<Pivot>()
    private val demandPivots = mutableListOf<Pivot>()

    // Parametry strategii
    private val ATR_Length = 20
    private val ATR_StopMultiplier = 6.0
    private val penetrationFilter = 0.5  // przykładowa wartość
    private val fixedQuantity = "0.01"
    private val cooldownPeriod: Long = 15 * 60 * 1000 // 15 minut
    private var lastTradeTimestamp: Long = 0
    private var activeTrade: Boolean = false

    fun start() {
        logger.info("Starting Livermore Strategy in {} mode...", if (simulationMode) "SIMULATION" else "LIVE")
        val callback = WebSocketCallback { response ->
            try {
                val klineData = json.decodeFromString(KlineData.serializer(), response)
                val candle = klineData.kline
                if (candle.isClosed) {
                    processCandle(candle)
                }
            } catch (e: Exception) {
                logger.error("Error processing WebSocket data: {}", e.message)
            }
        }
        // Subskrypcja 15-minutowych świec dla BTCUSDT
        val stream = websocketClient.klineStream("btcusdt", "15m", callback)
        try {
            sleep(3600000) // Uruchomiony przez 1 godzinę
        } catch (e: InterruptedException) {
            logger.error("WebSocket interrupted: {}", e.message)
        } finally {
            websocketClient.closeConnection(stream)
        }
    }

    private fun processCandle(candle: Kline) {
        candles.add(candle)
        if (candles.size > 100) candles.removeAt(0)

        updatePivots()

        if (candles.size >= ATR_Length) {
            val atr = calculateATR(candles.takeLast(ATR_Length))
            val noise = atr * penetrationFilter
            val now = System.currentTimeMillis()
            if (!activeTrade && now - lastTradeTimestamp >= cooldownPeriod) {
                val currentPrice = candle.openPrice.toDouble()
                // Warunek wejścia long: cena > (maksymalny z dwóch ostatnich supply pivotów + noise)
                if (supplyPivots.size >= 2) {
                    val recentSupply = supplyPivots.takeLast(2).maxOf { it.level }
                    if (currentPrice > recentSupply + noise) {
                        val entryPrice = currentPrice
                        val stopLoss = entryPrice - (atr * ATR_StopMultiplier)
                        val risk = entryPrice - stopLoss
                        val takeProfit = entryPrice + (risk * 2)
                        logger.info("Long Signal: Entry={}, Resistance={}, Noise={}, ATR={}",
                            entryPrice, recentSupply, noise, atr)
                        executeTrade("BUY", entryPrice, stopLoss, takeProfit)
                    }
                }
                // Warunek wejścia short: cena < (minimalny z dwóch ostatnich demand pivotów - noise)
                if (demandPivots.size >= 2) {
                    val recentDemand = demandPivots.takeLast(2).minOf { it.level }
                    if (currentPrice < recentDemand - noise) {
                        val entryPrice = currentPrice
                        val stopLoss = entryPrice + (atr * ATR_StopMultiplier)
                        val risk = stopLoss - entryPrice
                        val takeProfit = entryPrice - (risk * 2)
                        logger.info("Short Signal: Entry={}, Support={}, Noise={}, ATR={}",
                            entryPrice, recentDemand, noise, atr)
                        executeTrade("SELL", entryPrice, stopLoss, takeProfit)
                    }
                }
            }
        }
    }

    /**
     * Prosta detekcja pivotów:
     * - Supply Pivot: świeca, której high jest niższy od high świecy poprzedniej i następnej.
     * - Demand Pivot: świeca, której low jest wyższy od low świecy poprzedniej i następnej.
     */
    private fun updatePivots() {
        if (candles.size < 3) return
        val i = candles.size - 2
        val prev = candles[i - 1]
        val current = candles[i]
        val next = candles[i + 1]

        val currentHigh = current.highPrice.toDouble()
        val prevHigh = prev.highPrice.toDouble()
        val nextHigh = next.highPrice.toDouble()
        val currentLow = current.lowPrice.toDouble()
        val prevLow = prev.lowPrice.toDouble()
        val nextLow = next.lowPrice.toDouble()

        if (currentHigh < prevHigh && currentHigh < nextHigh) {
            if (supplyPivots.isEmpty() || supplyPivots.last().time != current.closeTime) {
                supplyPivots.add(Pivot(current.closeTime, currentHigh))
                logger.info("Supply Pivot detected at {}: {}", current.closeTime, currentHigh)
            }
        }
        if (currentLow > prevLow && currentLow > nextLow) {
            if (demandPivots.isEmpty() || demandPivots.last().time != current.closeTime) {
                demandPivots.add(Pivot(current.closeTime, currentLow))
                logger.info("Demand Pivot detected at {}: {}", current.closeTime, currentLow)
            }
        }
    }

    /**
     * Oblicza ATR (Average True Range) na podstawie listy świec.
     */
    private fun calculateATR(candles: List<Kline>): Double {
        if (candles.size < 2) return 0.0
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val high = candles[i].highPrice.toDouble()
            val low = candles[i].lowPrice.toDouble()
            val prevClose = candles[i - 1].closePrice.toDouble()
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trueRanges.add(tr)
        }
        return trueRanges.average()
    }

    /**
     * Wykonuje (lub symuluje) zlecenie rynkowe.
     * Parametr entryPrice jest używany do logowania oraz kalkulacji, bo przy market order Binance nie przyjmuje ceny wejścia.
     */
    private fun executeTrade(side: String, entryPrice: Double, stopLoss: Double, takeProfit: Double) {
        activeTrade = true
        lastTradeTimestamp = System.currentTimeMillis()

        if (simulationMode) {
            // W trybie symulacji nie wysyłamy zleceń, tylko logujemy potencjalne wyniki
            logger.info("Simulated trade: Side={}, EntryPrice={}, StopLoss={}, TakeProfit={}",
                side, entryPrice, stopLoss, takeProfit)
            val potentialProfit = if (side == "BUY") takeProfit - entryPrice else entryPrice - takeProfit
            logger.info("Potential profit per unit: {}", potentialProfit)
            // Możesz też zaktualizować stan portfela symulacyjnego, jeśli chcesz prowadzić symulację
            // Pomijamy ustawienie zleceń zabezpieczających
            activeTrade = false
            return
        }

        // Tryb live – wykonanie rzeczywistego zlecenia
        val parameters = LinkedHashMap<String, Any>()
        parameters["symbol"] = "BTCUSDT"
        parameters["side"] = side
        parameters["type"] = "MARKET"
        parameters["quantity"] = fixedQuantity

        try {
            val result = restClient.account().newOrder(parameters)
            logger.info("Trade executed at theoretical entry price {}: {}", entryPrice, result)
            setupRiskManagement(side, stopLoss, takeProfit)
        } catch (e: Exception) {
            logger.error("Trade execution failed: {}", e.message)
            activeTrade = false
        }
    }

    /**
     * Ustawia (lub symuluje) zlecenia zabezpieczające: stop-loss oraz take-profit.
     */
    private fun setupRiskManagement(side: String, stopLoss: Double, takeProfit: Double) {
        if (simulationMode) {
            logger.info("Simulated risk management: StopLoss={}, TakeProfit={}", stopLoss, takeProfit)
            return
        }
        try {
            val stopLossParams = LinkedHashMap<String, Any>()
            stopLossParams["symbol"] = "BTCUSDT"
            stopLossParams["side"] = if (side == "BUY") "SELL" else "BUY"
            stopLossParams["type"] = "STOP_MARKET"
            stopLossParams["stopPrice"] = stopLoss.toString()
            stopLossParams["closePosition"] = "true"
            val stopLossResult = restClient.account().newOrder(stopLossParams)
            logger.info("Stop-loss order placed: {}", stopLossResult)

            val takeProfitParams = LinkedHashMap<String, Any>()
            takeProfitParams["symbol"] = "BTCUSDT"
            takeProfitParams["side"] = if (side == "BUY") "SELL" else "BUY"
            takeProfitParams["type"] = "TAKE_PROFIT_MARKET"
            takeProfitParams["stopPrice"] = takeProfit.toString()
            takeProfitParams["closePosition"] = "true"
            val takeProfitResult = restClient.account().newOrder(takeProfitParams)
            logger.info("Take-profit order placed: {}", takeProfitResult)
        } catch (e: Exception) {
            logger.error("Risk management setup failed: {}", e.message)
        } finally {
            // Reset stanu transakcji po okresie monitorowania (symulowany cooldown)
            Thread {
                try {
                    sleep(15 * 60 * 1000)
                } catch (e: InterruptedException) {
                    logger.error("Monitoring interrupted: {}", e.message)
                }
                activeTrade = false
                logger.info("Ready for next trade.")
            }.start()
        }
    }
}
