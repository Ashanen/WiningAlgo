import backtest.backtestMultipleStrategiesOnePosition

import live.LiveTradingManager

fun main() {
    val isBacktest = true  // Toggle

    if (isBacktest) {
        backtestMultipleStrategiesOnePosition()
    } else {
        val manager = LiveTradingManager()
        manager.runLive()
    }
}







