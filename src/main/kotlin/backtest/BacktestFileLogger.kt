package backtest

import engine.StrategyManager
import model.TradeRecord
import org.slf4j.LoggerFactory
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlin.math.sqrt

object BacktestFileLogger {
    private val logger = LoggerFactory.getLogger(BacktestFileLogger::class.java)

    fun writeReport(manager: StrategyManager, extraLog: String) {
        val userHome = System.getProperty("user.home")
        val reportsDir = File("$userHome${File.separator}BacktestReports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        val timestamp = sdf.format(Date())
        val fileName = "BacktestReport_${timestamp}.log"
        val file = File(reportsDir, fileName)

        // Oblicz dodatkowe metryki z listy zarejestrowanych transakcji
        val tradeRecords = manager.tradeRecords
        val totalTrades = tradeRecords.size
        val wins = tradeRecords.count { it.profit >= 0 }
        val losses = tradeRecords.count { it.profit < 0 }
        val grossProfit = tradeRecords.filter { it.profit > 0 }.sumOf { it.profit }
        val grossLoss = tradeRecords.filter { it.profit < 0 }.sumOf { it.profit }
        val profitFactor = if (grossLoss != 0.0) grossProfit / -grossLoss else Double.POSITIVE_INFINITY
        val averageProfit = if (totalTrades > 0) tradeRecords.sumOf { it.profit } / totalTrades else 0.0

        val profitList = tradeRecords.map { it.profit }
        val meanProfit = if (profitList.isNotEmpty()) profitList.average() else 0.0
        val stdDevProfit = if (profitList.isNotEmpty()) sqrt(profitList.map { (it - meanProfit) * (it - meanProfit) }.average()) else 0.0
        val medianProfit = if (profitList.isNotEmpty()) {
            val sortedProfits = profitList.sorted()
            if (sortedProfits.size % 2 == 0) {
                (sortedProfits[sortedProfits.size / 2 - 1] + sortedProfits[sortedProfits.size / 2]) / 2
            } else {
                sortedProfits[sortedProfits.size / 2]
            }
        } else 0.0

        // Oblicz maksymalne obsunięcie (drawdown) symulując zmiany kapitału
        var runningCapital = 1000.0 // zakładamy startowy kapitał 1000.0
        val capitalValues = mutableListOf<Double>()
        capitalValues.add(runningCapital)
        val sortedTrades = tradeRecords.sortedBy { it.exitTime }
        for (trade in sortedTrades) {
            runningCapital += trade.profit
            capitalValues.add(runningCapital)
        }
        val maxCapital = capitalValues.maxOrNull() ?: runningCapital
        val drawdowns = capitalValues.map { maxCapital - it }
        val maxDrawdown = drawdowns.maxOrNull() ?: 0.0

        // Dodatkowa analiza według godziny wejścia
        val tradesByHour = tradeRecords.groupBy { getHourOfDay(it.entryTime) }
        val hourAnalysis = buildString {
            append("Analiza według godziny wejścia:\n")
            for (hour in 0..23) {
                val trades = tradesByHour[hour] ?: emptyList()
                if (trades.isNotEmpty()) {
                    val count = trades.size
                    val avgProfit = trades.sumOf { it.profit } / count
                    append("  Godzina $hour: Liczba transakcji = $count, Średni zysk = ${"%.2f".format(avgProfit)}\n")
                }
            }
        }

        // Dodatkowa analiza według wolumenu transakcji
        val volumes = tradeRecords.map { it.volume }
        val medianVolume = if (volumes.isNotEmpty()) {
            val sortedVolumes = volumes.sorted()
            if (sortedVolumes.size % 2 == 0) {
                (sortedVolumes[sortedVolumes.size / 2 - 1] + sortedVolumes[sortedVolumes.size / 2]) / 2
            } else {
                sortedVolumes[sortedVolumes.size / 2]
            }
        } else 0.0
        val lowVolumeTrades = tradeRecords.filter { it.volume < medianVolume }
        val highVolumeTrades = tradeRecords.filter { it.volume >= medianVolume }
        val lowVolumeAvgProfit = if (lowVolumeTrades.isNotEmpty()) lowVolumeTrades.sumOf { it.profit } / lowVolumeTrades.size else 0.0
        val highVolumeAvgProfit = if (highVolumeTrades.isNotEmpty()) highVolumeTrades.sumOf { it.profit } / highVolumeTrades.size else 0.0

        val volumeAnalysis = buildString {
            append("Analiza według wolumenu transakcji:\n")
            append("  Mediana wolumenu: ${"%.2f".format(medianVolume)}\n")
            append("  Niskowolumenowe transakcje (< mediana): Liczba = ${lowVolumeTrades.size}, Średni zysk = ${"%.2f".format(lowVolumeAvgProfit)}\n")
            append("  Wysokowolumenowe transakcje (>= mediana): Liczba = ${highVolumeTrades.size}, Średni zysk = ${"%.2f".format(highVolumeAvgProfit)}\n")
        }

        // Szczegółowa lista transakcji
        val tradesDetail = buildString {
            append("Szczegółowa lista transakcji:\n")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            for (trade in sortedTrades) {
                val entryTime = dateFormat.format(Date(trade.entryTime))
                val exitTime = dateFormat.format(Date(trade.exitTime))
                append("  Strategia: ${trade.strategyName}, Wejście: $entryTime, Wyjście: $exitTime, Zysk: ${"%.2f".format(trade.profit)}, Wolumen: ${"%.2f".format(trade.volume)}\n")
                if (trade.indicatorData != null) {
                    append("    Indicator Data: ${trade.indicatorData}\n")
                }
            }
        }

        // Budujemy treść raportu
        val content = buildString {
            append("Backtest Report\n")
            append("Time: $timestamp\n\n")
            append("=== Ogólne Wyniki ===\n")
            append(manager.extraLog())
            append("\n=== Dodatkowe Metryki ===\n")
            append("Łączna liczba transakcji: $totalTrades\n")
            append("Wygrane: $wins\n")
            append("Przegrane: $losses\n")
            append("Średni zysk na transakcję: ${"%.2f".format(averageProfit)}\n")
            append("Profit Factor: ${"%.2f".format(profitFactor)}\n")
            append("Mediana zysku: ${"%.2f".format(medianProfit)}\n")
            append("Odchylenie standardowe zysków: ${"%.2f".format(stdDevProfit)}\n")
            append("Maksymalne obsunięcie (Drawdown): ${"%.2f".format(maxDrawdown)}\n")
            append("\n=== Analiza zysków/strat wg strategii ===\n")
            val tradesByStrategy = tradeRecords.groupBy { it.strategyName }
            for ((strategyName, trades) in tradesByStrategy) {
                val stratTotal = trades.size
                val stratWins = trades.count { it.profit >= 0 }
                val stratLosses = trades.count { it.profit < 0 }
                val stratAvgProfit = if (trades.isNotEmpty()) trades.sumOf { it.profit } / trades.size else 0.0
                append("\nStrategia: $strategyName\n")
                append("  Transakcje: $stratTotal, Wygrane: $stratWins, Przegrane: $stratLosses\n")
                append("  Średni zysk: ${"%.2f".format(stratAvgProfit)}\n")
                trades.forEach { trade ->
                    if (trade.indicatorData != null) {
                        append("  Transakcja z ${trade.entryTime}: indicatorData = ${trade.indicatorData}\n")
                    }
                }
            }
            append("\n=== Analiza według godziny wejścia ===\n")
            append(hourAnalysis)
            append("\n=== Analiza według wolumenu transakcji ===\n")
            append(volumeAnalysis)
            append("\n=== Szczegółowa lista transakcji ===\n")
            append(tradesDetail)
            if (extraLog.isNotEmpty()) {
                append("\n=== Dodatkowe Informacje ===\n")
                append(extraLog)
            }
        }
        file.writeText(content)
        logger.info("Backtest report saved to ${file.absolutePath}")
    }

    private fun getHourOfDay(timestamp: Long): Int {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).hour
    }

    // Dodana funkcja generująca raport w formacie CSV
    fun writeCsvReport(manager: StrategyManager, extraLog: String) {
        val userHome = System.getProperty("user.home")
        val reportsDir = File("$userHome${File.separator}BacktestReports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        val timestamp = sdf.format(Date())
        val fileName = "BacktestReport_${timestamp}.csv"
        val file = File(reportsDir, fileName)

        // Pobieramy transakcje i sortujemy je po czasie wyjścia
        val tradeRecords = manager.tradeRecords.sortedBy { it.exitTime }

        // Przygotowujemy nagłówek oraz wiersze dla pliku CSV
        val header = listOf("Strategy", "Entry Time", "Exit Time", "Profit", "Volume", "Indicator Data")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val rows = mutableListOf<List<String>>()
        rows.add(header)

        for (trade in tradeRecords) {
            val entryTimeStr = dateFormat.format(Date(trade.entryTime))
            val exitTimeStr = dateFormat.format(Date(trade.exitTime))
            val profitStr = "%.2f".format(trade.profit)
            val volumeStr = "%.2f".format(trade.volume)
            val indicatorDataStr = trade.indicatorData?.toString() ?: ""
            rows.add(listOf(trade.strategyName, entryTimeStr, exitTimeStr, profitStr, volumeStr, indicatorDataStr))
        }

        // Zapisujemy dane do pliku CSV przy użyciu biblioteki kotlin-csv-jvm
        csvWriter().writeAll(rows, file)
        logger.info("CSV Backtest report saved to ${file.absolutePath}")
    }
}
