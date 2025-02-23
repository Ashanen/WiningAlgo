package analyzer

import model.Kline
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class LiquidityStats(
    var totalVolume: Double = 0.0,
    var count: Int = 0,
    var totalPriceChange: Double = 0.0
) {
    fun averageVolume(): Double = if (count > 0) totalVolume / count else 0.0
    fun averagePriceChange(): Double = if (count > 0) totalPriceChange / count else 0.0
}

class LiquidityAnalyzer {
    private val stats: MutableMap<Int, MutableMap<Int, LiquidityStats>> = mutableMapOf()

    fun processCandle(candle: Kline) {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.openTime), ZoneId.systemDefault())
        val dayOfWeek = zdt.dayOfWeek.value
        val hour = zdt.hour

        val volume = candle.volume.toDoubleOrNull() ?: 0.0
        val openPrice = candle.openPrice.toDoubleOrNull() ?: 0.0
        val closePrice = candle.closePrice.toDoubleOrNull() ?: 0.0
        val priceChange = closePrice - openPrice

        val dayStats = stats.getOrPut(dayOfWeek) { mutableMapOf() }
        val hourStats = dayStats.getOrPut(hour) { LiquidityStats() }
        hourStats.totalVolume += volume
        hourStats.totalPriceChange += priceChange
        hourStats.count += 1
    }

    fun generateReport(): String {
        val sb = StringBuilder()
        sb.append("Liquidity Analysis Report\n")
        sb.append("Dzień tygodnia (1=Pon, 7=Niedz) | Godzina | Średni wolumen | Średnia zmiana ceny\n")
        for (day in 1..7) {
            val dayStats = stats[day]
            if (dayStats != null) {
                for (hour in 0..23) {
                    val hourStats = dayStats[hour]
                    if (hourStats != null) {
                        sb.append("Dzień $day, Godzina $hour: Śr. wolumen = ${"%.2f".format(hourStats.averageVolume())}, Śr. zmiana = ${"%.2f".format(hourStats.averagePriceChange())}\n")
                    }
                }
            }
        }
        return sb.toString()
    }
}
