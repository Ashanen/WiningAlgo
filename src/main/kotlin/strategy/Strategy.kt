package strategy

import model.Kline
import model.OpenPosition
import model.StrategySignal

interface Strategy {
    val name: String

    fun onNewCandle(
        candle: Kline,
        candlesSoFar: List<Kline>,
        capital: Double
    ): List<StrategySignal>

    fun onUpdatePosition(
        candle: Kline,
        openPosition: OpenPosition
    ): List<StrategySignal>
}
