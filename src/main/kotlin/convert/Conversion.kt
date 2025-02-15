package convert

import model.Kline

fun List<Kline>.toCloseDouble(): List<Double> =
    this.map { it.closePrice.toDouble() }

fun List<Kline>.toHighDouble(): List<Double> =
    this.map { it.highPrice.toDouble() }

fun List<Kline>.toLowDouble(): List<Double> =
    this.map { it.lowPrice.toDouble() }

fun List<Kline>.toVolumeDouble(): List<Double> =
    this.map { it.volume.toDouble() }
