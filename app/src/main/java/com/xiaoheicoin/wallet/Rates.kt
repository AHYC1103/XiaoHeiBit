package com.xiaoheicoin.wallet

/**
 * 汇率（¥/枚）。后续可换成实时 API，先硬编码。
 */
object Rates {
    // symbol -> CNY per 1 coin
    val CNY = mapOf(
        "XHB" to 1.0,
        "TRX" to 2.31,
        "BNB" to 5344.65,
        "USDT" to 6.72,
    )

    fun cny(symbol: String): Double = CNY[symbol] ?: 0.0

    /** 格式化为 ¥ 字符串 */
    fun fmt(symbol: String, amount: Double): String {
        val v = amount * cny(symbol)
        return when {
            v >= 10000 -> "¥%.0f".format(v)
            v >= 1 -> "¥%.2f".format(v)
            else -> "¥%.4f".format(v)
        }
    }
}
