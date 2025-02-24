package parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import model.Kline
import org.slf4j.LoggerFactory

object CandleParser {
    private val logger = LoggerFactory.getLogger(CandleParser::class.java)
    private val mapper = jacksonObjectMapper()

    fun parseCandles(jsonString: String): List<Kline> {
        return try {
            val root = mapper.readTree(jsonString)
            if (!root.isArray) {
                logger.warn("CandleParser: JSON root is not an array")
                return emptyList()
            }
            val list = mutableListOf<Kline>()
            for (item in root) {
                if (item.isArray && item.size() >= 7) {
                    val openTime = item[0].asLong()
                    val openPrice = item[1].asText()
                    val highPrice = item[2].asText()
                    val lowPrice = item[3].asText()
                    val closePrice = item[4].asText()
                    val volume = item[5].asText()
                    val closeTime = item[6].asLong()
                    if (closePrice.toDoubleOrNull() == null) {
                        logger.warn("CandleParser: Invalid closePrice value: $closePrice; skipping candle")
                        continue
                    }
                    list += Kline(openTime, openPrice, highPrice, lowPrice, closePrice, volume, closeTime)
                }
            }
            list
        } catch (e: Exception) {
            logger.error("CandleParser: Error parsing JSON - ${e.message}", e)
            emptyList()
        }
    }
}
