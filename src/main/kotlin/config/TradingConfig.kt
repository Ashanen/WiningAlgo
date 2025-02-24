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

object StrategyParameters {
    // Parametry dla EnhancedAdaptiveMACDStrategy
    const val enhancedMacdFastPeriod: Int = 8
    const val enhancedMacdSlowPeriod: Int = 21
    const val enhancedMacdSignalPeriod: Int = 5
    const val rsiPeriod: Int = 10
    const val atrPeriod: Int = 14
    const val baseRiskPercent: Double = 0.01
    const val atrMultiplierSL: Double = 1.5
    const val atrMultiplierTP: Double = 3.0
    const val enhancedMacdUseAdaptive: Boolean = true

    // Parametry dodatkowych wskaźników dla EnhancedAdaptiveMACDStrategy
    const val useStochastic: Boolean = true
    const val stochasticPeriod: Int = 9
    const val stochasticDPeriod: Int = 3
    const val stochasticOverbought: Double = 75.0
    const val stochasticOversold: Double = 25.0

    const val useAdx: Boolean = true
    const val adxPeriod: Int = 14
    const val adxThreshold: Double = 15.0

    const val useIchimoku: Boolean = true
    const val ichimokuTenkanPeriod: Int = 9
    const val ichimokuKijunPeriod: Int = 26
    const val ichimokuSenkouSpanBPeriod: Int = 52
    const val ichimokuDisplacement: Int = 26

    const val useParabolicSar: Boolean = false

    // Parametry dla BollingerScalpingStrategy
    const val bollingerBbPeriod: Int = 20
    const val bollingerBbNumDevs: Double = 2.0
    const val bollingerEmaPeriod: Int = 50

    // Parametry dla RSIOverboughtOversoldTrendStrategy
    const val rsiOverbought: Int = 70
    const val rsiOversold: Int = 30
    const val rsiEmaPeriod: Int = 50
}



