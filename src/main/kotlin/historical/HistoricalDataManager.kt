package historical

import com.binance.connector.futures.client.impl.UMFuturesClientImpl
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import config.TradingConfig
import model.Kline
import org.slf4j.LoggerFactory
import parser.CandleParser.parseCandles
import java.io.File
import java.time.Instant

class HistoricalDataManager(
    private val futuresClient: UMFuturesClientImpl,
    private val symbol: String = "BTCUSDT",
    private val interval: String = "15m",
    private val yearsToFetch: Int = 6
) {
    private val logger = LoggerFactory.getLogger(HistoricalDataManager::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()

    // Plik docelowy (np. na pulpicie)
    private val outputFile: File by lazy {
        val desktop = System.getProperty("user.home") + File.separator + "Desktop"
        File("$desktop${File.separator}${symbol}_${interval}.json")
    }

    /**
     * Główna metoda zapewniająca aktualność danych:
     *  - Jeśli plik nie istnieje lub jest pusty => pobiera dane od 6 lat wstecz.
     *  - Jeśli istnieje => pobiera tylko brakujące dane od ostatniego closeTime.
     */
    fun ensureHistoricalDataUpToDate() {
        if (!outputFile.exists()) {
            logger.info("Plik {} nie istnieje. Pobieramy dane od {} lat wstecz...", outputFile.absolutePath, yearsToFetch)
            downloadFullHistoryAndSave()
        } else {
            val existingCandles = loadExistingCandles()
            if (existingCandles.isEmpty()) {
                logger.info("Plik {} istnieje, ale jest pusty. Pobieramy dane od {} lat wstecz.", outputFile.absolutePath, yearsToFetch)
                downloadFullHistoryAndSave()
            } else {
                // Mamy już dane w pliku, sprawdzamy brakujące
                val lastCloseTimeFromFile = existingCandles.last()[6].toLongOrNull() ?: 0L
                val endTime = System.currentTimeMillis()
                if (lastCloseTimeFromFile >= endTime) {
                    logger.info("Dane w pliku {} są aktualne (lastCloseTime={} >= endTime={}).", outputFile.name, lastCloseTimeFromFile, endTime)
                    return
                }
                logger.info("Pobieram brakujący zakres: od {} do {}", Instant.ofEpochMilli(lastCloseTimeFromFile + 1), Instant.ofEpochMilli(endTime))

                val newCandles = fetchAllDataFrom(lastCloseTimeFromFile + 1, endTime)
                logger.info("Pobrano {} brakujących świec.", newCandles.size)
                if (newCandles.isNotEmpty()) {
                    val updated = existingCandles.toMutableList().apply {
                        addAll(newCandles)
                    }
                    saveCandlesToFile(updated, outputFile)
                    logger.info("Zapisano łącznie {} świec w pliku {}.", updated.size, outputFile.absolutePath)
                } else {
                    logger.info("Brak nowych świec do pobrania, dane aktualne.")
                }
            }
        }
    }

    /**
     * Pomocnicza metoda do pobrania pełnych danych od 6 lat wstecz.
     */
    private fun downloadFullHistoryAndSave() {
        val endTime = System.currentTimeMillis()
        val sixYearsInMs = yearsToFetch.toLong() * 365L * 24L * 60L * 60L * 1000L
        val startTime = endTime - sixYearsInMs

        val allCandles = fetchAllDataFrom(startTime, endTime)
        logger.info("Pobrano w sumie {} świec. Zapis do pliku {}.", allCandles.size, outputFile.absolutePath)
        saveCandlesToFile(allCandles, outputFile)
    }

    /**
     * Pobiera wszystkie dane w zadanym przedziale [startMs, endMs].
     * Robi zapytania do Binance co 1000 świec (limit).
     */
    private fun fetchAllDataFrom(startMs: Long, endMs: Long): List<List<String>> {
        val allCandles = mutableListOf<List<String>>()
        var currentStart = startMs

        while (true) {
            val params = linkedMapOf<String, Any>(
                "symbol" to symbol,
                "interval" to interval,
                "limit" to 1000,
                "startTime" to currentStart
            )
            val result = futuresClient.market().klines(params)
            val parsed = parseRawKlines(result)
            if (parsed.isEmpty()) break

            allCandles.addAll(parsed)
            val lastClose = parsed.last()[6].toLongOrNull() ?: break
            if (lastClose >= endMs) break

            currentStart = lastClose + 1
        }
        return allCandles
    }

    /**
     * Ładuje istniejący plik JSON (jeśli istnieje) i zwraca surowe tablice (List<List<String>>).
     */
    fun loadExistingCandles(): List<List<String>> {
        if (!outputFile.exists()) {
            return emptyList()
        }
        return try {
            val root = mapper.readTree(outputFile)
            if (root is ArrayNode) {
                root.map { arrayNode ->
                    arrayNode.map { it.asText() }
                }
            } else emptyList()
        } catch (e: Exception) {
            logger.warn("Błąd wczytywania pliku {}, zaczynam od zera: {}", outputFile.absolutePath, e.message)
            emptyList()
        }
    }

    /**
     * Zapisuje listę klines (List<List<String>>) do pliku JSON.
     */
    private fun saveCandlesToFile(candles: List<List<String>>, file: File) {
        val arrayNode = mapper.createArrayNode()
        for (kline in candles) {
            val row = mapper.createArrayNode()
            kline.forEach { row.add(it) }
            arrayNode.add(row)
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, arrayNode)
    }

    /**
     * Konwertuje odpowiedź JSON-a z Binance (klines) do surowego formatu (List<List<String>>).
     */
    private fun parseRawKlines(jsonString: String): List<List<String>> {
        val root = mapper.readTree(jsonString)
        if (!root.isArray) return emptyList()
        val list = mutableListOf<List<String>>()
        for (item in root) {
            if (item.isArray) {
                val row = item.map { it.asText() }
                list.add(row)
            }
        }
        return list
    }

    /**
     * Metoda pomocnicza do konwersji surowych tablic (List<List<String>>) na listę obiektów Kline,
     * jeśli chcesz docelowo użyć parseCandles(result).
     * W praktyce parseCandles(...) z parsera przyjmuje surowy JSON, więc można od razu z pliku.
     */
    fun convertRawToKlines(raw: List<List<String>>): List<Kline> {
        // Wykorzystaj np. CandleParser.parseCandles(...) jeżeli chcesz
        // W tym przykładzie budujemy Kline "ręcznie".
        return raw.mapNotNull { row ->
            if (row.size < 7) null
            else Kline(
                openTime = row[0].toLongOrNull() ?: return@mapNotNull null,
                closeTime = row[6].toLongOrNull() ?: return@mapNotNull null,
                openPrice = row[1],
                highPrice = row[2],
                lowPrice = row[3],
                closePrice = row[4],
                volume = row[5],
                isClosed = true
            )
        }
    }
}
