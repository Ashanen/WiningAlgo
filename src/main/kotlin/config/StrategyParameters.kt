package config

sealed interface StrategyParameters {
    // Parametry dla Bollinger Scalping Strategy
    data class BollingerScalpingParams(
        val bbPeriod: Int = 20,
        val bbNumDevs: Double = 2.0,
        val emaPeriod: Int = 50,
        val atrPeriod: Int = 14,
        val baseRiskPercent: Double = 0.01,
        val atrMultiplierSL: Double = 1.0,
        val atrMultiplierTP: Double = 3.0
    ) : StrategyParameters

    // Parametry dla Enhanced Adaptive MACD Strategy
    data class EnhancedAdaptiveMACDParams(
        val fastPeriod: Int = 12,
        val slowPeriod: Int = 26,
        val signalPeriod: Int = 9,
        val rsiPeriod: Int = 14,
        val atrPeriod: Int = 14,
        val baseRiskPercent: Double = 0.01,
        val atrMultiplierSL: Double = 1.0,
        val atrMultiplierTP: Double = 3.0,
        val useAdaptive: Boolean = true,
        val useStochastic: Boolean = true,
        val stochasticPeriod: Int = 14,
        val stochasticDPeriod: Int = 3,
        val stochasticOverbought: Double = 80.0,
        val stochasticOversold: Double = 20.0,
        val useAdx: Boolean = true,
        val adxPeriod: Int = 14,
        val adxThreshold: Double = 20.0,
        val useIchimoku: Boolean = true,
        val tenkanPeriod: Int = 9,
        val kijunPeriod: Int = 26,
        val senkouSpanBPeriod: Int = 52,
        val ichimokuDisplacement: Int = 26,
        val useParabolicSar: Boolean = false
    ) : StrategyParameters

    // Parametry dla RSI Overbought Oversold Trend Strategy
    data class RSIOverboughtOversoldParams(
        val rsiPeriod: Int = 14,
        val overbought: Int = 80,
        val oversold: Int = 20,
        val emaPeriod: Int = 50,
        val atrPeriod: Int = 14,
        val baseRiskPercent: Double = 0.01,
        val atrMultiplierSL: Double = 1.0,
        val atrMultiplierTP: Double = 3.0
    ) : StrategyParameters
}