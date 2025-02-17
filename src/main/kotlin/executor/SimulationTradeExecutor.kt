package executor

class SimulationTradeExecutor : TradeExecutor {

    override fun openTrade(
        side: String,
        quantity: Double,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit: Double
    ): Boolean {
        // W symulacji nic nie robimy poza logami
        println("SIMULATION: openTrade($side, qty=$quantity, entry=$entryPrice)")
        return true
    }

    override fun closeTrade(openSide: String, quantity: Double, exitPrice: Double): Boolean {
        println("SIMULATION: closeTrade($openSide, qty=$quantity, exit=$exitPrice)")
        return true
    }
}
