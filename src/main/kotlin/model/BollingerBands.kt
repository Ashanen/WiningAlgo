package model

data class BollingerBands(
    val middle: List<Double>,
    val upper: List<Double>,
    val lower: List<Double>
)
