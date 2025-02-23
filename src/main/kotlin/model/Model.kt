package model

import analyzer.LiquidityStats

data class Kline(
    val openTime: Long,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String? = null,
    val numberOfTrades: Int? = null,
    val takerBuyBaseAssetVolume: String? = null,
    val takerBuyQuoteAssetVolume: String? = null,
    val isClosed: Boolean = true
)

data class StrategySignal(
    val type: SignalType,
    val price: Double,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val quantity: Double = 0.0,
    val indicatorData: Map<String, Any>? = null
)

enum class SignalType {
    BUY, SELL, CLOSE
}

data class OpenPosition(
    val side: String,
    val entryPrice: Double,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val quantity: Double,
    var maxFavorable: Double = 0.0,
    var minFavorable: Double = Double.MAX_VALUE,
    val openTime: Long = 0L,
    val strategyName: String = "",
    val indicatorData: Map<String, Any>? = null
)

data class TradeRecord(
    val strategyName: String,
    val entryTime: Long,
    val exitTime: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val profit: Double,
    val durationMillis: Long,
    val indicatorData: Map<String, Any>? = null,
    val volume: Double = 0.0,
    val liquidityStats: LiquidityStats? = null
)
