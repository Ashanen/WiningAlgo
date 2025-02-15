package executor

class SimulationTradeExecutor : TradeExecutor {
    override fun openTrade(side: String, quantity: Double, entryPrice: Double, stopLoss: Double, takeProfit: Double): Boolean {
        // For simulation, simply return true.
        return true
    }

    override fun closeTrade(openSide: String, quantity: Double, exitPrice: Double): Boolean {
        return true
    }
}