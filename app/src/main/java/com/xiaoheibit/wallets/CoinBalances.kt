package com.xiaoheibit.wallets

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 其他币种余额查询（对接公链RPC，多节点 fallback）
 */
object CoinBalances {

    val TRON_NODES get() = RemoteConfig.tronNodes
    val BSC_NODES get() = RemoteConfig.bscNodes

    private fun post(url: String, body: String, timeout: Int = 3000): String? {
        return try {
            val t0 = System.currentTimeMillis()
            val c = (URL(url).openConnection() as HttpURLConnection)
            c.requestMethod = "POST"
            c.connectTimeout = timeout; c.readTimeout = timeout
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            c.disconnect()
            LogUtil.d("CoinBalances", "POST $url code=$code cost=${System.currentTimeMillis()-t0}ms resp=${text.take(150)}")
            if (code in 200..299) text else null
        } catch (e: Exception) {
            LogUtil.e("CoinBalances", "POST $url error: ${e.message}")
            null
        }
    }

    /** TRON 主网余额（TRX 6位小数），多节点 fallback */
    fun tronBalance(addr: String): Double {
        val body = JSONObject().put("address", addr).put("visible", true).toString()
        for (node in TRON_NODES) {
            val text = post("$node/wallet/getaccount", body) ?: continue
            try {
                val j = JSONObject(text)
                val balance = j.optLong("balance", 0)
                return balance / 1_000_000.0
            } catch (_: Exception) { continue }
        }
        return -1.0
    }

    /** BSC 余额（BNB 18位小数），多节点 fallback */
    fun bscBalance(addr: String): Double {
        val data = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "eth_getBalance")
            .put("params", org.json.JSONArray().put(addr).put("latest"))
            .put("id", 1)
            .toString()
        for (node in BSC_NODES) {
            val text = post(node, data) ?: continue
            try {
                val j = JSONObject(text)
                val hex = j.optString("result", "0x0")
                return java.math.BigInteger(hex.removePrefix("0x"), 16).toDouble() / 1e18
            } catch (_: Exception) { continue }
        }
        return -1.0
    }

    /** USDT(TRC20) 余额，从 trongrid account 接口的 trc20 字段解析 */
    fun usdtBalance(ownerAddr: String): Double {
        val contractAddr = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        val body = JSONObject().put("address", ownerAddr).put("visible", true).toString()
        for (node in TRON_NODES) {
            val text = post("$node/wallet/getaccount", body) ?: continue
            try {
                val j = JSONObject(text)
                val trc20 = j.optJSONArray("trc20")
                if (trc20 != null) {
                    for (i in 0 until trc20.length()) {
                        val entry = trc20.getJSONObject(i)
                        val key = entry.keys().next()
                        if (key == contractAddr) {
                            val bal = entry.optString(key, "0").toLongOrNull() ?: 0
                            return bal / 1_000_000.0  // USDT 6位小数
                        }
                    }
                }
                return 0.0  // 账户存在但没有USDT
            } catch (_: Exception) { continue }
        }
        return -1.0
    }
}
