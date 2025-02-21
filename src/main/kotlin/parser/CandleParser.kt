package parser

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import model.Kline
import org.slf4j.LoggerFactory

object CandleParser {
    private val logger = LoggerFactory.getLogger(CandleParser::class.java)
    private val mapper = jacksonObjectMapper()

    fun parseCandles(jsonString: String): List<Kline> {
        val root = mapper.readTree(jsonString)
        if (!root.isArray) return emptyList()

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
                val quoteAssetVolume = if (item.size() > 7) item[7].asText() else null
                val numberOfTrades = if (item.size() > 8) item[8].asInt() else null
                val takerBuyBase = if (item.size() > 9) item[9].asText() else null
                val takerBuyQuote = if (item.size() > 10) item[10].asText() else null

                list += Kline(
                    openTime = openTime,
                    openPrice = openPrice,
                    highPrice = highPrice,
                    lowPrice = lowPrice,
                    closePrice = closePrice,
                    volume = volume,
                    closeTime = closeTime,
                    quoteAssetVolume = quoteAssetVolume,
                    numberOfTrades = numberOfTrades,
                    takerBuyBaseAssetVolume = takerBuyBase,
                    takerBuyQuoteAssetVolume = takerBuyQuote,
                    isClosed = true
                )
            }
        }
        return list
    }

    fun parseSingleCandleFromWs(jsonString: String): Kline? {
        return try {
            val node = mapper.readTree(jsonString)
            val kNode = node["k"] ?: return null

            val openTime = kNode["t"].asLong()
            val closeTime = kNode["T"].asLong()
            val openPrice = kNode["o"].asText()
            val closePrice = kNode["c"].asText()
            val highPrice = kNode["h"].asText()
            val lowPrice = kNode["l"].asText()
            val volume = kNode["v"].asText()
            val isClosed = kNode["x"].asBoolean()
            val quoteAssetVolume = kNode["q"]?.asText()
            val numberOfTrades = kNode["n"]?.asInt()
            val takerBuyBase = kNode["V"]?.asText()
            val takerBuyQuote = kNode["Q"]?.asText()

            Kline(
                openTime = openTime,
                openPrice = openPrice,
                highPrice = highPrice,
                lowPrice = lowPrice,
                closePrice = closePrice,
                volume = volume,
                closeTime = closeTime,
                quoteAssetVolume = quoteAssetVolume,
                numberOfTrades = numberOfTrades,
                takerBuyBaseAssetVolume = takerBuyBase,
                takerBuyQuoteAssetVolume = takerBuyQuote,
                isClosed = isClosed
            )
        } catch (e: Exception) {
            logger.error("Error parsing single candle from ws: ${e.message}")
            null
        }
    }
}
