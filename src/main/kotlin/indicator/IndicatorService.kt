package indicator

import config.StrategyParameters
import model.Kline
import compute.Indicators

data class IndicatorResults(
    val macd: Indicators.MacdResult?,
    val adaptiveMacd: Indicators.MacdResult?,
    val rsi: List<Double>?,
    val bollingerBands: Indicators.BollingerBandsResult?,
    val stochastic: Indicators.StochasticResult?,
    val ichimoku: Indicators.IchimokuResult?,
    val adx: List<Double>?,
    val atr: List<Double>?,
    val pivotPoints: Indicators.PivotPointsResult?
)

object IndicatorService {
    /**
     * Oblicza wszystkie wybrane wskaźniki na podstawie listy świec.
     * Jeśli ilość danych jest niewystarczająca, dany wskaźnik zwraca null lub pustą listę.
     */
    fun computeIndicators(candles: List<Kline>): IndicatorResults {
        if (candles.isEmpty()) {
            return IndicatorResults(null, null, null, null, null, null, null, null, null)
        }
        val closePrices = candles.map { it.closePrice.toDouble() }

        val macdResult = if (closePrices.size >= 26) {
            Indicators.computeMacd(closePrices, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
        } else null

        val adaptiveMacdResult = if (closePrices.size >= 26) {
            Indicators.computeAdaptiveMacd(closePrices, baseFast = 12, baseSlow = 26, baseSignal = 9)
        } else null

        val rsiResult = if (closePrices.size >= 15) {
            Indicators.computeRsi(closePrices, period = 14)
        } else null

        val bbResult = if (closePrices.size >= 20) {
            Indicators.computeBollingerBands(closePrices, period = 20, numDevs = 2.0)
        } else null

        val stochasticResult = if (candles.size >= StrategyParameters.stochasticPeriod) {
            Indicators.computeStochasticOscillator(candles, StrategyParameters.stochasticPeriod, StrategyParameters.stochasticDPeriod)
        } else null

        val ichimokuResult = if (candles.size >= StrategyParameters.senkouSpanBPeriod) {
            Indicators.computeIchimoku(candles, StrategyParameters.tenkanPeriod, StrategyParameters.kijunPeriod, StrategyParameters.senkouSpanBPeriod, StrategyParameters.ichimokuDisplacement)
        } else null

        val adxResult = if (StrategyParameters.useAdx) {
            Indicators.computeAdx(candles, StrategyParameters.adxPeriod)
        } else emptyList()

        val atrResult = if (StrategyParameters.useATR) {
            Indicators.computeAtr(candles, StrategyParameters.atrPeriod)
        } else emptyList()

        val pivotPointsResult = if (candles.isNotEmpty()) {
            Indicators.computePivotPoints(candles.last())
        } else null

        return IndicatorResults(
            macd = macdResult,
            adaptiveMacd = adaptiveMacdResult,
            rsi = rsiResult,
            bollingerBands = bbResult,
            stochastic = stochasticResult,
            ichimoku = ichimokuResult,
            adx = adxResult,
            atr = atrResult,
            pivotPoints = pivotPointsResult
        )
    }
}
