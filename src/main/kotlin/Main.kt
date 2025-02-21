import backtest.BacktestRunner


fun main() {
    val isBacktest = true
    if (isBacktest) {
        BacktestRunner.backtestMultipleStrategiesOnePosition(rangeYears = 2)
    } else {

    }
}







