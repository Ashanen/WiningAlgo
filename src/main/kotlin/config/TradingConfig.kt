package config

object TradingConfig {
    const val SYMBOL = "BTCUSDT"
    const val INTERVAL = "15m"
    const val YEARS_TO_FETCH = 6
    // Domyślny limit zapytania do Binance
    const val LIMIT = 1000

    // Bazowy folder na Desktopie
    fun getBaseDataDir(symbol: String, interval: String): String {
        val desktop = System.getProperty("user.home") + "/Desktop"
        return "$desktop/Data/$symbol/$interval"
    }
}


