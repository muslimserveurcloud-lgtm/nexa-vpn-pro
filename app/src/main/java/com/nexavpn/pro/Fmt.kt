package com.nexavpn.pro

import java.util.Locale

fun fmtBytes(n: Long): String {
    if (n < 1024) return "$n o"
    val units = arrayOf("Ko", "Mo", "Go", "To")
    var v = n.toDouble()
    var i = -1
    do {
        v /= 1024
        i++
    } while (v >= 1024 && i < units.size - 1)
    return String.format(Locale.FRANCE, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}
