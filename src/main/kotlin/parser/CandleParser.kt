package parser

import model.Kline
import org.json.JSONObject
import org.json.JSONArray

object CandleParser {

    /**
     * Existing method: parse an entire JSON array of klines from the REST API.
     */
    fun parseCandles(json: String): List<Kline> {
        val arr = JSONArray(json)
        val list = mutableListOf<Kline>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONArray(i)
            val kline = jsonArrayToKline(item)
            list.add(kline)
        }
        return list
    }

    /**
     * Helper that converts a single JSON array item -> Kline.
     * (Already used by parseCandles)
     */
    private fun jsonArrayToKline(item: JSONArray): Kline {
        val openTime = item.getLong(0)
        val openPrice = item.getString(1)
        val highPrice = item.getString(2)
        val lowPrice = item.getString(3)
        val closePrice = item.getString(4)
        val volume = item.getString(5)
        val closeTime = item.getLong(6)
        // ...
        // last boolean for isClosed can be deduce from openTime/closeTime or you can do:
        val isClosed = true // Because from REST, these are fully closed klines

        return Kline(
            openTime = openTime,
            closeTime = closeTime,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            closePrice = closePrice,
            volume = volume,
            isClosed = isClosed
        )
    }

    /**
     * Minimal addition: parse a single 'k' object from the WebSocket JSON event.
     * This method reuses the same fields for continuity with parseCandles(...).
     */
    fun parseSingleCandleFromWs(json: String): Kline? {
        val root = JSONObject(json)
        if (!root.has("k")) return null

        val kObj = root.getJSONObject("k")

        val openTime = kObj.getLong("t")
        val closeTime = kObj.getLong("T")
        val openPrice = kObj.getString("o")
        val highPrice = kObj.getString("h")
        val lowPrice = kObj.getString("l")
        val closePrice = kObj.getString("c")
        val volume = kObj.getString("v")
        val isClosed = kObj.getBoolean("x")  // 'x' indicates if kline is closed

        return Kline(
            openTime = openTime,
            closeTime = closeTime,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            closePrice = closePrice,
            volume = volume,
            isClosed = isClosed
        )
    }
}
