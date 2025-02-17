package model

import strategy.SignalType

data class Signal(
    val index: Int,
    val type: SignalType,
    val reason: String
)