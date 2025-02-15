package parser

import kotlinx.serialization.json.*
import model.Kline
import org.slf4j.Logger

fun parseCandles(result: String, logger: Logger): List<Kline> {
    val json = Json { ignoreUnknownKeys = true }

    val list = mutableListOf<Kline>()
    try {
        val jsonElement = json.parseToJsonElement(result)
        when {
            jsonElement is JsonArray &&
                    jsonElement.isNotEmpty() &&
                    jsonElement[0] is JsonArray -> {

                for (element in jsonElement) {
                    if (element is JsonArray) {
                        val openTime   = element[0].jsonPrimitive.long
                        val openPrice  = element[1].jsonPrimitive.content
                        val highPrice  = element[2].jsonPrimitive.content
                        val lowPrice   = element[3].jsonPrimitive.content
                        val closePrice = element[4].jsonPrimitive.content
                        val volume     = element[5].jsonPrimitive.content
                        val closeTime  = element[6].jsonPrimitive.long
                        val isClosed = true

                        list.add(
                            Kline(
                                openTime   = openTime,
                                closeTime  = closeTime,
                                openPrice  = openPrice,
                                highPrice  = highPrice,
                                lowPrice   = lowPrice,
                                closePrice = closePrice,
                                volume     = volume,
                                isClosed   = isClosed
                            )
                        )
                    }
                }
            }

            jsonElement is JsonObject && jsonElement.containsKey("k") -> {

                val klineObj = jsonElement["k"]!!.jsonObject
                val openTime   = klineObj["t"]!!.jsonPrimitive.long
                val closeTime  = klineObj["T"]!!.jsonPrimitive.long
                val openPrice  = klineObj["o"]!!.jsonPrimitive.content
                val highPrice  = klineObj["h"]!!.jsonPrimitive.content
                val lowPrice   = klineObj["l"]!!.jsonPrimitive.content
                val closePrice = klineObj["c"]!!.jsonPrimitive.content
                val volume     = klineObj["v"]!!.jsonPrimitive.content
                val isClosed   = klineObj["x"]!!.jsonPrimitive.boolean

                list.add(
                    Kline(
                        openTime   = openTime,
                        closeTime  = closeTime,
                        openPrice  = openPrice,
                        highPrice  = highPrice,
                        lowPrice   = lowPrice,
                        closePrice = closePrice,
                        volume     = volume,
                        isClosed   = isClosed
                    )
                )
            }

            // 3) JSON Array z obiektami zawierającymi "k"
            jsonElement is JsonArray &&
                    jsonElement.isNotEmpty() &&
                    jsonElement[0].jsonObject.containsKey("k") -> {

                for (element in jsonElement) {
                    val klineObj = element.jsonObject["k"]!!.jsonObject
                    val openTime   = klineObj["t"]!!.jsonPrimitive.long
                    val closeTime  = klineObj["T"]!!.jsonPrimitive.long
                    val openPrice  = klineObj["o"]!!.jsonPrimitive.content
                    val highPrice  = klineObj["h"]!!.jsonPrimitive.content
                    val lowPrice   = klineObj["l"]!!.jsonPrimitive.content
                    val closePrice = klineObj["c"]!!.jsonPrimitive.content
                    val volume     = klineObj["v"]!!.jsonPrimitive.content
                    val isClosed   = klineObj["x"]!!.jsonPrimitive.boolean

                    list.add(
                        Kline(
                            openTime   = openTime,
                            closeTime  = closeTime,
                            openPrice  = openPrice,
                            highPrice  = highPrice,
                            lowPrice   = lowPrice,
                            closePrice = closePrice,
                            volume     = volume,
                            isClosed   = isClosed
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Error parsing candles: {}", e.message)
    }
    return list
}