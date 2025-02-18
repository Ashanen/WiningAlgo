// File: strategy/BollingerBandBreakoutStrategyDynamic.kt

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl
import com.binance.connector.futures.client.utils.WebSocketCallback
import compute.BollingerBands
import executor.TradeExecutor
import kotlinx.serialization.json.*
import model.Kline
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import parser.CandleParser.parseCandles
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class BollingerBandBreakoutStrategyDynamic(
    private val futuresClient: UMFuturesClientImpl,
    private val websocketClient: UMWebsocketClientImpl,
    private val tradeExecutor: TradeExecutor
) {

    private val logger = LoggerFactory.getLogger(BollingerBandBreakoutStrategyDynamic::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    // Strategy parameters
    private val bbPeriod = 20
    private val bbMultiplier = 2.0
    private val lookbackPeriod = 20
    private val riskRewardRatio = 3.0

    // Capital management parameters
    private var capital = 1000.0
    private val riskPercentage = 0.05
    private val minRiskPerUnitMultiplier = 0.025

    // Leverage
    private val leverage = 3.0

    // List of candles
    private val candles = mutableListOf<Kline>()

    data class Trade(
        val side: String,
        val entryPrice: Double,
        var maxFavorable: Double,
        var minFavorable: Double,
        val stopLoss: Double,
        val takeProfit: Double,
        val quantity: Double
    )
    private var openTrade: Trade? = null

    private val quantityFormatter = DecimalFormat("#.########").apply { roundingMode = RoundingMode.HALF_UP }

    private fun computeRSI(candles: List<Kline>, period: Int = 14): Double {
        if (candles.size < period + 1) return 50.0
        val closes = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (closes.size < period + 1) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in closes.size - period until closes.size) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gains += diff else losses += -diff
        }
        val avgGain = gains / period
        val avgLoss = losses / period
        val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
        return 100 - (100 / (1 + rs))
    }

    private fun computeBollingerBands(prices: List<Double>, period: Int, multiplier: Double): BollingerBands {
        val middle = MutableList(prices.size) { 0.0 }
        val upper = MutableList(prices.size) { 0.0 }
        val lower = MutableList(prices.size) { 0.0 }
        for (i in prices.indices) {
            if (i < period - 1) {
                middle[i] = prices.take(i + 1).average()
                upper[i] = middle[i]
                lower[i] = middle[i]
            } else {
                val window = prices.subList(i - period + 1, i + 1)
                val sma = window.average()
                middle[i] = sma
                val sd = sqrt(window.map { (it - sma) * (it - sma) }.average())
                upper[i] = sma + multiplier * sd
                lower[i] = sma - multiplier * sd
            }
        }
        return BollingerBands(middle, upper, lower)
    }

    private fun calculateATR(candles: List<Kline>): Double {
        if (candles.size < 2) return 0.0
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val high = candles[i].highPrice.toDoubleOrNull() ?: continue
            val low = candles[i].lowPrice.toDoubleOrNull() ?: continue
            val prevClose = candles[i - 1].closePrice.toDoubleOrNull() ?: continue
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trueRanges.add(tr)
        }
        return if (trueRanges.isNotEmpty()) trueRanges.average() else 0.0
    }

    private fun updateTrailingStop(candle: Kline) {
        val price = candle.closePrice.toDoubleOrNull() ?: return
        val trade = openTrade ?: return
        when (trade.side) {
            "BUY" -> if (price > trade.maxFavorable) trade.maxFavorable = price
            "SELL" -> if (price < trade.minFavorable) trade.minFavorable = price
        }
    }

    private fun checkForExit(candle: Kline) {
        val price = candle.closePrice.toDoubleOrNull() ?: return
        val trade = openTrade ?: return
        val atr = calculateATR(candles.takeLast(lookbackPeriod))
        when (trade.side) {
            "BUY" -> {
                val trailingStop = trade.maxFavorable - atr
                if (price <= trailingStop || price >= trade.takeProfit || price <= trade.stopLoss) {
                    if (tradeExecutor.closeTrade(trade.side, trade.quantity, price)) {
                        exitTrade(price)
                    } else {
                        logger.error("Error closing LONG trade at price: {}", price)
                    }
                }
            }
            "SELL" -> {
                val trailingStop = trade.minFavorable + atr
                if (price >= trailingStop || price <= trade.takeProfit || price >= trade.stopLoss) {
                    if (tradeExecutor.closeTrade(trade.side, trade.quantity, price)) {
                        exitTrade(price)
                    } else {
                        logger.error("Error closing SHORT trade at price: {}", price)
                    }
                }
            }
        }
    }

    private fun exitTrade(exitPrice: Double) {
        val trade = openTrade ?: return
        val profit = when (trade.side) {
            "BUY" -> (exitPrice - trade.entryPrice) * trade.quantity
            "SELL" -> (trade.entryPrice - exitPrice) * trade.quantity
            else -> 0.0
        }
        capital += profit
        logger.info("Closed trade at price: {}. Profit/Loss: {}. New capital: {}", exitPrice, profit, capital)
        openTrade = null
    }

    fun runBacktest() {
        logger.info("Starting backtest. Initial capital: {} USD", capital)
        val params = LinkedHashMap<String, Any>().apply {
            put("symbol", "BTCUSDT")
            put("interval", "5m")
            put("limit", "1000")
            put("leverage", leverage)
        }
        val result = futuresClient.market().klines(params)
        val historicalCandles = parseCandles(result)
        logger.info("Loaded {} historical candles.", historicalCandles.size)
        candles.clear()
        for (candle in historicalCandles) {
            processCandle(candle)
        }
        logger.info("Backtest finished. Final capital: {} USD", capital)
    }

    fun runLive() {
        logger.info("Loading historical candles for live trading...")
        val params = LinkedHashMap<String, Any>().apply {
            put("symbol", "BTCUSDT")
            put("interval", "5m")
            put("limit", "1000")
        }
        val result = futuresClient.market().klines(params)
        val historicalCandles = parseCandles(result)
        logger.info("Loaded {} historical candles.", historicalCandles.size)
        candles.clear()
        candles.addAll(historicalCandles)
        logger.info("Subscribing to live data. Current capital: {} USD", capital)
        val callback = WebSocketCallback { response ->
            try {
                val newCandles = parseCandles("[$response]")
                if (newCandles.isNotEmpty()) {
                    val newCandle = newCandles[0]
                    if (newCandle.isClosed) {
                        logger.info("Received live candle: {}", response)
                        processCandle(newCandle)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing live data: {}", e.message)
            }
        }
        websocketClient.klineStream("btcusdt", "5m", callback)
        logger.info("Subscribed to live candle stream.")
        while (true) {
            Thread.sleep(1000)
        }
    }

    private fun processCandle(candle: Kline) {
        if (!candle.isClosed) return

        if (candles.isEmpty() || candle.openTime > candles.last().openTime) {
            candles.add(candle)
            if (candles.size > 1000) candles.removeAt(0)
        }

        val price = candle.closePrice.toDoubleOrNull() ?: return
        val recentClosePrices = candles.mapNotNull { it.closePrice.toDoubleOrNull() }
        if (recentClosePrices.size < bbPeriod) return

        val bb = computeBollingerBands(recentClosePrices.takeLast(bbPeriod), bbPeriod, bbMultiplier)
        val upperBand = bb.upper.last()
        val lowerBand = bb.lower.last()

        val rsi = computeRSI(candles, 14)

        if (openTrade != null) {
            updateTrailingStop(candle)
            checkForExit(candle)
        } else {
            if (price > upperBand && rsi > 50) {
                val entryPrice = price
                val riskAmount = max(capital * riskPercentage, 100.0)
                val atr = calculateATR(candles.takeLast(lookbackPeriod))
                val riskPerUnit = max(atr, entryPrice * minRiskPerUnitMultiplier)
                if (riskPerUnit <= 0) return
                val stopLoss = entryPrice - riskPerUnit
                val takeProfit = entryPrice + riskPerUnit * riskRewardRatio
                val quantity = riskAmount / riskPerUnit
                if (tradeExecutor.openTrade("BUY", quantity, entryPrice, stopLoss, takeProfit)) {
                    openTrade = Trade("BUY", entryPrice, entryPrice, entryPrice, stopLoss, takeProfit, quantity)
                    logger.info("Opened LONG: price={}, SL={}, TP={}, qty={}, RSI={}",
                        entryPrice, stopLoss, takeProfit, quantityFormatter.format(quantity), rsi)
                } else {
                    logger.error("Error opening LONG at price: {}", entryPrice)
                }
            } else if (price < lowerBand && rsi < 50) {
                val entryPrice = price
                val riskAmount = min(capital * riskPercentage, 100.0)
                val atr = calculateATR(candles.takeLast(lookbackPeriod))
                val riskPerUnit = max(atr, entryPrice * minRiskPerUnitMultiplier)
                if (riskPerUnit <= 0) return
                val stopLoss = entryPrice + riskPerUnit
                val takeProfit = entryPrice - riskPerUnit * riskRewardRatio
                val quantity = riskAmount / riskPerUnit
                if (tradeExecutor.openTrade("SELL", quantity, entryPrice, stopLoss, takeProfit)) {
                    openTrade = Trade("SELL", entryPrice, entryPrice, entryPrice, stopLoss, takeProfit, quantity)
                    logger.info("Opened SHORT: price={}, SL={}, TP={}, qty={}, RSI={}",
                        entryPrice, stopLoss, takeProfit, quantityFormatter.format(quantity), rsi)
                } else {
                    logger.error("Error opening SHORT at price: {}", entryPrice)
                }
            }
        }
    }
}
