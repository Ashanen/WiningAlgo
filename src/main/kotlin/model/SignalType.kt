package model

enum class SignalType {
    BUY, SELL, NONE
}

data class Signal(
    val index: Int,
    val type: SignalType,
    val reason: String
)