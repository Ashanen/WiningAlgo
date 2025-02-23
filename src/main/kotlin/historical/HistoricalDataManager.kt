package historical

import parser.CandleParser
import model.Kline
import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoricalDataManager(
    private val futuresClient: UMFuturesClientImpl,
    val symbol: String,
    val interval: String
) {
    private val mapper = jacksonObjectMapper()

    fun getIntervalDir(): File {
        val userHome = System.getProperty("user.home")
        val baseDir = File("$userHome${File.separator}Data${File.separator}$symbol${File.separator}$interval")
        if (!baseDir.exists()) baseDir.mkdirs()
        return baseDir
    }

    fun ensureYearlyDataUpToDate(rangeYears: Int = 6) {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        val currentYear = now.year
        val startYear = currentYear - rangeYears + 1

        for (year in startYear..currentYear) {
            updateYearData(year)
        }
    }

    private fun updateYearData(year: Int) {
        val intervalDir = getIntervalDir()
        val fileName = "${symbol}_${interval}_$year.json"
        val file = File(intervalDir, fileName)

        val existingCandles = if (file.exists()) loadExistingRaw(file) else emptyList<List<String>>()

        val yearStart = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val yearEnd = ZonedDateTime.of(year, 12, 31, 23, 59, 59, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

        val lastCloseFromFile = existingCandles.lastOrNull()?.get(6)?.toLongOrNull() ?: 0L
        val startFetch = if (lastCloseFromFile > yearStart) lastCloseFromFile + 1 else yearStart

        println("Updating data for year=$year, from=$startFetch to=$yearEnd")

        if (startFetch > yearEnd) {
            println("Already have all data for year=$year")
            return
        }

        val newRaw = mutableListOf<List<String>>()
        var currentStart = startFetch
        while (true) {
            if (currentStart > yearEnd) break

            val params = linkedMapOf<String, Any>(
                "symbol" to symbol,
                "interval" to interval,
                "limit" to 1000,
                "startTime" to currentStart,
                "endTime" to yearEnd
            )
            val result = futuresClient.market().klines(params)
            val parsed = parseRawKlines(result)
            if (parsed.isEmpty()) break

            newRaw.addAll(parsed)
            val lastClose = parsed.last()[6].toLongOrNull() ?: break
            if (lastClose >= yearEnd) break
            currentStart = lastClose + 1
        }

        if (newRaw.isNotEmpty()) {
            println("Fetched ${newRaw.size} new klines for year=$year")
            val updated = existingCandles.toMutableList().apply {
                addAll(newRaw)
            }
            saveRawToFile(updated, file)
        } else {
            println("No new klines for year=$year")
        }
    }

    private fun parseRawKlines(jsonString: String): List<List<String>> {
        val node = mapper.readTree(jsonString)
        if (!node.isArray) return emptyList()
        val list = mutableListOf<List<String>>()
        for (arr in node) {
            if (arr.isArray && arr.size() >= 7) {
                val row = arr.map { it.asText() }
                list.add(row)
            }
        }
        return list
    }

    private fun loadExistingRaw(file: File): List<List<String>> {
        return try {
            val node = mapper.readTree(file)
            if (node is ArrayNode) {
                node.map { child ->
                    child.map { it.asText() }
                }
            } else emptyList()
        } catch (e: Exception) {
            println("Error reading file ${file.name}: ${e.message}")
            emptyList()
        }
    }

    private fun saveRawToFile(candles: List<List<String>>, file: File) {
        val arrayNode = mapper.createArrayNode()
        for (kline in candles) {
            val row = mapper.createArrayNode()
            kline.forEach { row.add(it) }
            arrayNode.add(row)
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, arrayNode)
        println("Saved ${candles.size} klines to ${file.absolutePath}")
    }

    fun loadAllYearData(rangeYears: Int = 6): List<Kline> {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))
        val currentYear = now.year
        val startYear = currentYear - rangeYears + 1

        val intervalDir = getIntervalDir()
        val allKlines = mutableListOf<Kline>()

        for (year in startYear..currentYear) {
            val fileName = "${symbol}_${interval}_$year.json"
            val file = File(intervalDir, fileName)
            if (!file.exists()) {
                println("File not found: ${file.name}, skipping year=$year")
                continue
            }
            val raw = loadExistingRaw(file)
            val jsonStr = mapper.writeValueAsString(raw)
            val klines = CandleParser.parseCandles(jsonStr)
            allKlines.addAll(klines)
            println("Reading file: ${file.name}, found ${klines.size} klines.")
        }

        return allKlines.sortedBy { it.openTime }
    }
}
