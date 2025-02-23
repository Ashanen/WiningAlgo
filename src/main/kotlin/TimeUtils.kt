import java.time.Instant
import java.time.ZoneId

object TimeUtils {
    fun isTradingTime(timestamp: Long): Boolean {
        val instant = Instant.ofEpochMilli(timestamp)
        val zonedDateTime = instant.atZone(ZoneId.of("UTC"))
        val dayOfWeek = zonedDateTime.dayOfWeek.value // 1 = poniedziałek, 7 = niedziela
        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        // Handel tylko w dni robocze (1-5) i w godzinach 15:00-18:00 UTC
        return dayOfWeek in 1..5 && (hour in 15..17 || (hour == 18 && minute == 0))
    }
}