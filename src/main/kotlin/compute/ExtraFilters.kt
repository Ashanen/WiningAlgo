package compute

import model.Kline
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

object ExtraFilters {

    /**
     * Sprawdza, czy godzina zamknięcia świecy (UTC) mieści się w [startHour, endHour).
     * Jeżeli startHour < endHour => normalny zakres (np. 6..23).
     * Jeżeli startHour > endHour => zakres "zawija" przez północ (np. 22..4).
     */
    fun isInTradingHours(candle: Kline, startHour: Int, endHour: Int): Boolean {
        val utcHour = Instant.ofEpochMilli(candle.closeTime)
            .atZone(ZoneOffset.UTC).hour

        return if (startHour < endHour) {
            (utcHour in startHour until endHour)
        } else {
            // przykład, jeśli chcemy handlować np. 22:00..04:00
            (utcHour >= startHour || utcHour < endHour)
        }
    }

    /**
     * Oblicza średni wolumen z ostatnich `period` świec.
     * Jeśli za mało świec lub wolumen = null => Double.NaN
     */
    fun averageVolume(candles: List<Kline>, period: Int): Double {
        if (candles.size < period) return Double.NaN
        val sub = candles.takeLast(period)
        val volumes = sub.mapNotNull { it.volume.toDoubleOrNull() }
        return if (volumes.isEmpty()) Double.NaN else volumes.average()
    }

    fun computeTradeQuantity(
        closePrice: Double?,
        baseRiskUsd: Double = 200.0,
        maxLeverage: Double = 3.0
    ): Double {
        // Gdy cena jest null lub <= 0, zwracamy 0
        if (closePrice == null || closePrice <= 0.0) return 0.0

        // Wyliczamy nominal w USD (np. 200 USD * 3 = 600 USD)
        val notionalUsd = baseRiskUsd * maxLeverage

        // Ilość = (nominal w USD) / (aktualna cena)
        // Np. 600 USD / 96000 USDT/BTC = ~0.00625 BTC
        val qty = notionalUsd / closePrice

        return qty
    }
}
