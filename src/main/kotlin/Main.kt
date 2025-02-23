import backtest.BacktestRunner

fun main() {
    val isBacktest = true
    if (isBacktest) {
        BacktestRunner.runBacktest(rangeYears = 2)
    } else {
        // Tutaj można umieścić kod do handlu na żywo
    }
}
