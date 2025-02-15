package trading

import model.Kline
import model.Signal

interface TradingStrategy {
    val name: String
    fun generateSignals(klines: List<Kline>): List<Signal>
}
