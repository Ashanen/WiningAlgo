package model

/**
 * Reprezentuje świecę (kline) z Binance.
 * openTime, closeTime => Long
 * openPrice, highPrice, lowPrice, closePrice, volume => String
 * isClosed => czy świeca jest zamknięta
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
 */
data class StrategySignal(
    val type: SignalType,
    val price: Double,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val quantity: Double = 0.0
)

enum class SignalType {
    BUY, SELL, CLOSE
}

/**
 * Informacje o otwartej pozycji.
 */
data class OpenPosition(
    val side: String,             // "BUY" lub "SELL"
    val entryPrice: Double,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val quantity: Double,
    var maxFavorable: Double = 0.0,
    var minFavorable: Double = Double.MAX_VALUE
)
