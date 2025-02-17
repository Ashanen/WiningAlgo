package model

data class Kline(
    val openTime: Long,
    val closeTime: Long,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val isClosed: Boolean
)

/**
 * Sygnał strategii (BUY, SELL, CLOSE) z parametrami.
 */
data class StrategySignal(
    val type: SignalType,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double
)

enum class SignalType {
    BUY, SELL, CLOSE
}

/**
 * Informacje o otwartej pozycji.
 */
data class OpenPosition(
    val side: String,          // "BUY" lub "SELL"
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double,
    var maxFavorable: Double,
    var minFavorable: Double
)
