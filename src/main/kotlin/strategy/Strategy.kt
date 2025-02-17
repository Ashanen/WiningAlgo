package strategy

import model.Kline
import model.OpenPosition

enum class SignalType { BUY, SELL, CLOSE }

data class StrategySignal(
    val type: SignalType,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double
)

interface Strategy {
    val name: String

    fun onNewCandle(candle: Kline, candles: List<Kline>, capital: Double): List<StrategySignal>

    fun onUpdatePosition(candle: Kline, openPosition: OpenPosition): List<StrategySignal>
}
