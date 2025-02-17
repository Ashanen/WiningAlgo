package executor

class SimulationTradeExecutor : TradeExecutor {
    override fun openTrade(side: String, quantity: Double, entryPrice: Double, stopLoss: Double, takeProfit: Double): Boolean {
        // Symulacja: zawsze true
        return true
    }

    override fun closeTrade(openSide: String, quantity: Double, exitPrice: Double): Boolean {
        // Symulacja: zawsze true
        return true
    }
}
