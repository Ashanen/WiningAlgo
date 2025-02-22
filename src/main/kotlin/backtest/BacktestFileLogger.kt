package backtest

import engine.StrategyManager
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object BacktestFileLogger {
    private val logger = LoggerFactory.getLogger(BacktestFileLogger::class.java)

    /**
     * Zapisuje raport do pliku, np. "BacktestReport_2025-02-21_18-40-23.log"
     */
    fun writeReport(manager: StrategyManager, extraLog: String) {
        val userHome = System.getProperty("user.home")
        val reportsDir = File("$userHome${File.separator}BacktestReports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        val timestamp = sdf.format(Date())
        val fileName = "BacktestReport_${timestamp}.log"
        val file = File(reportsDir, fileName)

        val content = buildString {
            append("Backtest Report\n")
            append("Time: $timestamp\n")
            append(manager.extraLog())
            if (extraLog.isNotEmpty()) {
                append("\nExtra:\n$extraLog\n")
            }
        }
        file.writeText(content)
        logger.info("Backtest report saved to ${file.absolutePath}")
    }
}
