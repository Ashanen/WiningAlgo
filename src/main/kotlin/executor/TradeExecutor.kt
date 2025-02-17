package executor

interface TradeExecutor {
    fun openTrade(side: String, quantity: Double, entryPrice: Double, stopLoss: Double, takeProfit: Double): Boolean
    fun closeTrade(openSide: String, quantity: Double, exitPrice: Double): Boolean
}
