package analyzer

import model.Kline
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Klasa przechowująca statystyki dla danego dnia i godziny.
 */
data class LiquidityStats(
    var totalVolume: Double = 0.0,
    var count: Int = 0,
    var totalPriceChange: Double = 0.0
) {
    fun averageVolume(): Double = if (count > 0) totalVolume / count else 0.0
    fun averagePriceChange(): Double = if (count > 0) totalPriceChange / count else 0.0
}

/**
 * Moduł zbiera statystyki dotyczące wolumenu i zmiany ceny dla każdej świecy,
 * pogrupowane według dnia tygodnia (1 = poniedziałek, 7 = niedziela) i godziny.
 */
class LiquidityAnalyzer {
    // Mapowanie: dzień tygodnia -> godzina -> statystyki
    private val stats: MutableMap<Int, MutableMap<Int, LiquidityStats>> = mutableMapOf()

    /**
     * Przetwarza pojedynczą świecę, rejestrując:
     * - Wolumen (próba konwersji z String do Double)
     * - Zmianę ceny (różnica między ceną otwarcia a ceną zamknięcia)
     * - Czas świecy – przeliczamy na dzień tygodnia oraz godzinę
     */
    fun processCandle(candle: Kline) {
        // Konwersja czasu otwarcia do strefy lokalnej
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.openTime), ZoneId.systemDefault())
        val dayOfWeek = zdt.dayOfWeek.value  // 1 (poniedziałek) do 7 (niedziela)
        val hour = zdt.hour

        val volume = candle.volume.toDoubleOrNull() ?: 0.0
        val openPrice = candle.openPrice.toDoubleOrNull() ?: 0.0
        val closePrice = candle.closePrice.toDoubleOrNull() ?: 0.0
        val priceChange = closePrice - openPrice

        // Pobieramy lub tworzymy statystyki dla danego dnia i godziny
        val dayStats = stats.getOrPut(dayOfWeek) { mutableMapOf() }
        val hourStats = dayStats.getOrPut(hour) { LiquidityStats() }

        hourStats.totalVolume += volume
        hourStats.totalPriceChange += priceChange
        hourStats.count += 1
    }

    /**
     * Generuje raport z analizą płynności – dla każdego dnia tygodnia i godziny wyświetla średni wolumen i średnią zmianę ceny.
     */
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
