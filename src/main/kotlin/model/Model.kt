package model

/**
 * Reprezentuje świecę (kline) z Binance.
 */
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

/**
 * Sygnał strategii (BUY, SELL, CLOSE) z parametrami.
 * Dodaliśmy pole indicatorData, które może zawierać dodatkowe dane wskaźnikowe.
 */
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

/**
 * Informacje o otwartej pozycji.
 * Dodaliśmy pole indicatorData oraz openTime i strategyName.
 */
data class OpenPosition(
    val side: String,             // "BUY" lub "SELL"
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

/**
 * Rejestr pojedynczego handlu – zawiera szczegóły potrzebne do dalszej analizy.
 */
data class TradeRecord(
    val strategyName: String,
    val entryTime: Long,
    val exitTime: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val profit: Double,
    val durationMillis: Long,
    val indicatorData: Map<String, Any>? = null
)
