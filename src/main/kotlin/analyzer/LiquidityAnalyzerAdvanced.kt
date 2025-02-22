package analyzer

import model.Kline
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.sqrt

data class AdvancedLiquidityStats(
    var totalVolume: Double = 0.0,
    var totalVolumeSq: Double = 0.0,
    var totalPriceChange: Double = 0.0,
    var totalPriceChangeSq: Double = 0.0,
    var count: Int = 0
) {
    fun averageVolume(): Double = if (count > 0) totalVolume / count else 0.0
    fun stdVolume(): Double {
        if (count == 0) return 0.0
        val avg = averageVolume()
        val variance = totalVolumeSq / count - avg * avg
        return if (variance > 0) sqrt(variance) else 0.0
    }

    fun averagePriceChange(): Double = if (count > 0) totalPriceChange / count else 0.0
    fun stdPriceChange(): Double {
        if (count == 0) return 0.0
        val avg = averagePriceChange()
        val variance = totalPriceChangeSq / count - avg * avg
        return if (variance > 0) sqrt(variance) else 0.0
    }
}

class LiquidityAnalyzerAdvanced {
    // Grupujemy dane według dnia tygodnia (1-7) i godziny (0-23)
    private val stats: MutableMap<Int, MutableMap<Int, AdvancedLiquidityStats>> = mutableMapOf()

    fun processCandle(candle: Kline) {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.openTime), ZoneId.systemDefault())
        val dayOfWeek = zdt.dayOfWeek.value  // 1 = poniedziałek, ... 7 = niedziela
        val hour = zdt.hour

        val volume = candle.volume.toDoubleOrNull() ?: 0.0
        val openPrice = candle.openPrice.toDoubleOrNull() ?: 0.0
        val closePrice = candle.closePrice.toDoubleOrNull() ?: 0.0
        val priceChange = closePrice - openPrice

        val dayStats = stats.getOrPut(dayOfWeek) { mutableMapOf() }
        val hourStats = dayStats.getOrPut(hour) { AdvancedLiquidityStats() }
        hourStats.totalVolume += volume
        hourStats.totalVolumeSq += volume * volume
        hourStats.totalPriceChange += priceChange
        hourStats.totalPriceChangeSq += priceChange * priceChange
        hourStats.count++
    }

    fun getStatsFor(dayOfWeek: Int, hour: Int): AdvancedLiquidityStats? {
        return stats[dayOfWeek]?.get(hour)
    }

    fun generateReport(): String {
        val sb = StringBuilder()
        sb.append("Advanced Liquidity Analysis Report\n")
        sb.append("Dzień tygodnia (1=Pon, 7=Niedz) | Godzina | Średni wolumen | Std wolumenu | Śr. zmiana ceny | Std zmiany ceny\n")
        for (day in 1..7) {
            val dayStats = stats[day]
            if (dayStats != null) {
                for (hour in 0..23) {
                    val hourStats = dayStats[hour]
                    if (hourStats != null) {
                        sb.append("Dzień $day, Godzina $hour: Śr. wolumen = ${"%.2f".format(hourStats.averageVolume())}, Std = ${"%.2f".format(hourStats.stdVolume())}, Śr. zmiana = ${"%.2f".format(hourStats.averagePriceChange())}, Std zmiany = ${"%.2f".format(hourStats.stdPriceChange())}\n")
                    }
                }
            }
        }
        return sb.toString()
    }
}
