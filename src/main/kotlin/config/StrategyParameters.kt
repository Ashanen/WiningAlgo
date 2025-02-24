package config

object StrategyParameters {
    // Parametry MACD i RSI
    const val enhancedMacdUseAdaptive: Boolean = true
    const val macdBuyThreshold: Double = 0.15
    const val macdSellThreshold: Double = -0.15
    const val rsiNeutral: Double = 50.0

    // Parametry dla Stochastic
    const val stochasticPeriod: Int = 14
    const val stochasticDPeriod: Int = 3
    const val stochasticOversold: Double = 20.0
    const val stochasticOverbought: Double = 80.0
    const val useStochastic: Boolean = true

    // Parametry ADX
    const val adxPeriod: Int = 14
    const val adxThreshold: Double = 25.0
    const val useAdx: Boolean = true

    // Parametry Ichimoku
    const val tenkanPeriod: Int = 9
    const val kijunPeriod: Int = 26
    const val senkouSpanBPeriod: Int = 52
    const val ichimokuDisplacement: Int = 26
    const val useIchimoku: Boolean = true

    // Parametr Parabolic SAR
    const val useParabolicSar: Boolean = true

    // Parametry ATR
    const val atrPeriod: Int = 14
    const val useATR: Boolean = true

    // Parametry PivotPoints
    const val usePivotPoints: Boolean = true

    // Parametry dla Bollinger Scalping Strategy (używane w innej strategii)
    const val bbPeriod: Int = 20
    const val bbMultiplier: Double = 2.0
    const val atrMultiplierSL_BB: Double = 1.0
    const val atrMultiplierTP_BB: Double = 2.0

    // Parametry dla RSIOverboughtOversoldTrendStrategy (używane w innej strategii)
    const val rsiOversold: Double = 30.0
    const val rsiOverbought: Double = 70.0
    const val atrMultiplierSL_RSI: Double = 1.0
    const val atrMultiplierTP_RSI: Double = 2.0

    // Parametry stop loss / take profit (fallback)
    const val stopLossPercent: Double = 0.005   // 0.5%
    const val takeProfitPercent: Double = 0.015   // 1.5%

    // Parametr ryzyka (procent kapitału) na transakcję
    const val riskPerTrade: Double = 0.01
    const val baseRiskPercent: Double = 0.01

    // Parametr dla wolumenu – bieżący wolumen musi być >= średniego * ratio
    const val volumeRatioThreshold: Double = 1.0
}
