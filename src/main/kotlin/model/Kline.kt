package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Kline(
    @SerialName("t") val openTime: Long,
    @SerialName("T") val closeTime: Long,
    @SerialName("o") val openPrice: String,
    @SerialName("h") val highPrice: String,
    @SerialName("l") val lowPrice: String,
    @SerialName("c") val closePrice: String,
    @SerialName("v") val volume: String,
    @SerialName("x") val isClosed: Boolean
)
