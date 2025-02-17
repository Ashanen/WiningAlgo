package model


data class OpenPosition(
    val side: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double,
    var maxFavorable: Double,
    var minFavorable: Double
)
