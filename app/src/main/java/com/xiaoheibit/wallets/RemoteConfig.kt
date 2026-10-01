package com.xiaoheibit.wallets

import android.content.Context
import org.json.JSONObject

/** 远程配置：启动时从 /api/config 拉取，缓存到本地 */
object RemoteConfig {
    private const val PREF = "remote_cfg"

    @Volatile var tronNodes: List<String> = listOf(
        "https://api.trongrid.io", "https://api.frankfurt.trongrid.io",
        "https://tron-rpc.publicnode.com", "https://api.tronstack.io"
    )
    @Volatile var bscNodes: List<String> = listOf(
        "https://bsc-rpc.publicnode.com", "https://rpc.ankr.com/bsc",
        "https://bsc.meowrpc.com", "https://1rpc.io/bnb"
    )
    @Volatile var rates: Map<String, Double> = mapOf(
        "XHB" to 1.0, "TRX" to 2.31, "BNB" to 5344.65, "USDT" to 6.72
    )
    @Volatile var fixedFees: Map<String, Double> = mapOf(
        "TRX" to 0.00001, "BNB" to 0.00001, "USDT" to 0.00001
    )
    @Volatile var feeThresholds: Map<String, Double> = mapOf(
        "TRX" to 10.0, "BNB" to 0.001, "USDT" to 5.0
    )
    @Volatile var feeOwnerTrx: String = ""
    @Volatile var feeOwnerBnb: String = ""
    @Volatile var swapPoolAddress: String = ""

    fun load(ctx: Context) {
        // 先读缓存
        val sp = ctx.getSharedPreferences(PREF, 0)
        if (sp.getString("json", null) != null) applyJson(sp.getString("json", null)!!)
        // 后台拉新配置
        Thread {
            try {
                val j = Api.get("/api/config")
                applyJson(j.toString())
                sp.edit().putString("json", j.toString()).apply()
            } catch (_: Exception) {}
        }.start()
    }

    private fun applyJson(text: String) {
        val j = JSONObject(text)
        val ta = j.getJSONArray("tron_nodes")
        tronNodes = (0 until ta.length()).map { ta.getString(it) }
        val ba = j.getJSONArray("bsc_nodes")
        bscNodes = (0 until ba.length()).map { ba.getString(it) }
        val ro = j.getJSONObject("rates")
        rates = ro.keys().asSequence().associateWith { ro.getDouble(it) }
        val fo = j.getJSONObject("fixed_fees")
        fixedFees = fo.keys().asSequence().associateWith { fo.getDouble(it) }
        val to = j.getJSONObject("fee_thresholds")
        feeThresholds = to.keys().asSequence().associateWith { to.getDouble(it) }
        feeOwnerTrx = j.optString("fee_owner_trx", "")
        feeOwnerBnb = j.optString("fee_owner_bnb", "")
        swapPoolAddress = j.optString("swap_pool_address", "")
    }

    fun rateOf(sym: String) = rates[sym] ?: 0.0
    fun feeOf(sym: String) = fixedFees[sym] ?: 0.00001
    fun thresholdOf(sym: String) = feeThresholds[sym] ?: 0.0
}
