import multi.BollingerScalpingStrategy
import multi.BreakoutStrategy
import multi.MeanReversionStrategy
import parser.parseCandles

fun main() {
//    // 1) Wczytaj JSON -> parseCandles(...)
//    val jsonResult = /* np. z binance API */
//    val klines = parseCandles(jsonResult)
//
//    // 2) Mamy listę strategii
//    val strategies = listOf(
//        BollingerScalpingStrategy(),
//        BreakoutStrategy(),
//        MeanReversionStrategy()
//    )
//
//    // 3) Uruchom każdą strategię
//    for (strategy in strategies) {
//        val signals = strategy.generateSignals(klines)
//        println("Strategy: ${strategy.name}, signals count = ${signals.size}")
//        signals.forEach { s ->
//            println("  Candle #${s.index} => ${s.type} (${s.reason})")
//        }
//    }
}