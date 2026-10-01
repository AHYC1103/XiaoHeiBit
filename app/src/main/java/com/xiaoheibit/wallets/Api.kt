package com.xiaoheibit.wallets

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 网络层 + 全局状态，对应 webui 的 api() 与 STATE */
object Api {
    private const val PREF = "xhb"
    private const val KEY_URL = "base_url"
    const val DEFAULT_URL = "http://cn-hk-bgp-4.ofalias.net:18222"

    @Volatile var baseUrl = DEFAULT_URL
    @Volatile var online = false

    fun load(ctx: Context) {
        val saved = ctx.getSharedPreferences(PREF, 0).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        // 旧版默认值迁移到公开主链
        baseUrl = if (saved == "http://127.0.0.1:8222") DEFAULT_URL else saved
    }
    fun saveUrl(ctx: Context, url: String) {
        baseUrl = url.trim().trimEnd('/')
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY_URL, baseUrl).apply()
    }
    /** 主网/测试网切换：只换端口（保留 host） */
    fun switchPort(ctx: Context, port: Int) {
        val host = Regex("://([^:/]+)").find(baseUrl)?.groupValues?.get(1) ?: "127.0.0.1"
        saveUrl(ctx, "http://$host:$port")
    }
    fun currentPort(): Int = Regex(":(\\d+)/?$").find(baseUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 8234

    private fun conn(path: String, method: String, body: String?): HttpURLConnection {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        c.connectTimeout = 2000
        c.readTimeout = 4000
        c.requestMethod = method
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return c
    }

    /** 同步请求，返回 JSONObject；error 字段或非2xx抛异常。后台线程调用 */
    fun req(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val t0 = System.currentTimeMillis()
        val url = baseUrl + path
        LogUtil.d("Api", "→ $method $url body=${body?.toString()?.take(200)}")
        val c = conn(path, method, body?.toString())
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        val cost = System.currentTimeMillis() - t0
        LogUtil.d("Api", "← $method $path code=$code cost=${cost}ms resp=${text.take(300)}")
        val j = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (j.optString("error").isNotBlank()) {
            LogUtil.e("Api", "$method $path error: ${j.getString("error")}")
            throw RuntimeException(j.getString("error"))
        }
        if (code !in 200..299) {
            LogUtil.e("Api", "$method $path HTTP $code")
            throw RuntimeException("HTTP $code")
        }
        return j
    }
    fun get(path: String) = req(path)
    fun post(path: String, body: JSONObject = JSONObject()) = req(path, "POST", body)

    // ===== 业务接口（均同步，后台线程调用） =====
    fun status() = get("/api/status")
    fun wallets() = get("/api/wallets")
    fun txs(addr: String, page: Int = 1, size: Int = 20) =
        get("/api/txs/$addr?page=$page&size=$size")
    fun validate() = get("/api/validate")
    fun createWallet(type: String) =
        post("/api/wallet/create", JSONObject().put("type", type).put("name",
            if (type == "mnemonic") "助记词钱包" else "钱包"))
    fun importKey(key: String) =
        post("/api/wallet/import", JSONObject().put("private_key", key).put("name", "导入钱包"))
    fun importMnemonic(m: String) =
        post("/api/wallet/import", JSONObject().put("type", "mnemonic").put("mnemonic", m).put("name", "助记词钱包"))
    fun switchWallet(index: Int) = post("/api/wallet/switch", JSONObject().put("index", index))
    fun renameWallet(index: Int, name: String, icon: String) =
        post("/api/wallet/rename", JSONObject().put("index", index).put("name", name).put("icon", icon))
    fun deleteWallet(index: Int) = post("/api/wallet/delete", JSONObject().put("index", index))
    fun export(index: Int, type: String) =
        post("/api/wallet/export", JSONObject().put("index", index).put("type", type))
    fun transfer(to: String, amount: Int) =
        post("/api/transfer", JSONObject().put("receiver", to).put("amount", amount))
    fun mine(count: Int) = post("/api/mine", JSONObject().put("count", count))
    fun nftList(addr: String) = get("/api/nft/list?address=$addr")
    fun nftMint(addr: String, name: String, cid: String, supply: Int, desc: String) =
        post("/api/nft/mint", JSONObject().put("address", addr).put("name", name).put("cid", cid).put("supply", supply).put("description", desc))
    fun nftSend(addr: String, id: Int) = post("/api/nft/send", JSONObject().put("address", addr).put("id", id))
    fun nftRecv(code: String) = post("/api/nft/recv", JSONObject().put("code", code))
    fun nftTransfer(addr: String, id: Int, to: String) = post("/api/nft/transfer", JSONObject().put("address", addr).put("id", id).put("to", to))
}

/** 钱包信息 */
data class WInfo(val index: Int, val address: String, val name: String,
                 val icon: String, val balance: Int, val current: Boolean)

/** 交易记录 */
data class Tx(val height: Int, val sender: String, val receiver: String,
              val amount: Int, val ts: Long, val pending: Boolean, val signature: String)

/** 全局可观察状态 */
object State {
    var wallets = listOf<WInfo>()
    var current: String? = null
    var height = 0
    var difficulty = 0
    var mempool = 0
    var supply = 0
    var reward = 50
    var txs = listOf<Tx>()
    var txTotal = 0
    var nfts = listOf<JSONObject>()
    var coinBalances = mapOf<String, Double>()  // symbol -> 余额

    fun cur(): WInfo? = wallets.firstOrNull { it.address == current }

    /** 拉取 status + 本地钱包余额 + txs，返回是否在线。后台线程 */
    fun refresh(): Boolean {
        return try {
            val ws = WalletStore.list
            val cur = WalletStore.current()
            current = cur?.address

            // 并行查询 status / balance / txs
            var statusJson = JSONObject()
            var balJson = JSONObject()
            var txsJson = JSONObject()
            val t1 = Thread { try { statusJson = Api.status() } catch (_: Exception) {} }
            val t2 = Thread { try { cur?.let { balJson = Api.get("/api/balance/${it.address}") } } catch (_: Exception) {} }
            val t3 = Thread { try { current?.let { txsJson = Api.txs(it) } } catch (_: Exception) {} }
            t1.start(); t2.start(); t3.start()
            t1.join(4000); t2.join(4000); t3.join(4000)

            height = statusJson.optInt("height")
            difficulty = statusJson.optInt("difficulty")
            mempool = statusJson.optInt("mempool")
            supply = statusJson.optInt("supply")
            reward = statusJson.optInt("miner_reward", 50)

            val curBal = balJson.optInt("balance")
            wallets = ws.mapIndexed { i, w ->
                val bal = if (w.address == current) curBal else 0
                WInfo(i, w.address, w.name, w.icon.ifBlank { "💰" }, bal, w.address == current)
            }

            txTotal = txsJson.optInt("total")
            val out = ArrayList<Tx>()
            val cf = txsJson.optJSONArray("confirmed")
            for (i in 0 until (cf?.length() ?: 0)) {
                val x = cf!!.getJSONObject(i)
                out.add(Tx(x.optInt("block_height"), x.optString("sender"), x.optString("receiver"),
                    x.optInt("amount"), x.optLong("timestamp"), false, x.optString("signature")))
            }
            val pd = txsJson.optJSONArray("pending")
            for (i in 0 until (pd?.length() ?: 0)) {
                val x = pd!!.getJSONObject(i)
                out.add(Tx(-1, x.optString("sender"), x.optString("receiver"),
                    x.optInt("amount"), x.optLong("timestamp"), true, ""))
            }
            out.sortByDescending { it.ts }
            txs = out
            // 其他币种余额（助记词=全链，私钥=仅导入链）
            val curW = WalletStore.current()
            val mn = curW?.mnemonic
            val priv = curW?.privateKeyHex ?: ""
            val impChain = curW?.importedChain
            if (mn == null && priv.isEmpty()) {
                State.coinBalances = emptyMap()
            } else {
                val balances = HashMap<String, Double>()
                // 助记词钱包查所有链；私钥钱包只查导入链（TRX含USDT）
                val allowed = when {
                    mn != null -> setOf("TRX", "BNB", "USDT")
                    impChain == "TRX" -> setOf("TRX", "USDT")
                    impChain == "BNB" -> setOf("BNB")
                    else -> emptySet()
                }
                // 并行查询各链余额
                val results = HashMap<String, Double>()
                val lock = Object()
                val addrIdx = curW?.addrIndex ?: 0
                val threads = allowed.map { sym ->
                    Thread {
                        try {
                            val b = when (sym) {
                                "TRX" -> {
                                    val addr = if (mn != null) MultiCoin.tronAddress(mn, MultiCoin.COINS.first { it.symbol == "TRX" }, addrIdx) else MultiCoin.tronAddressFromPriv(priv)
                                    CoinBalances.tronBalance(addr)
                                }
                                "BNB" -> {
                                    val addr = if (mn != null) MultiCoin.bscAddress(mn, MultiCoin.COINS.first { it.symbol == "BNB" }, addrIdx) else MultiCoin.bscAddressFromPriv(priv)
                                    CoinBalances.bscBalance(addr)
                                }
                                "USDT" -> {
                                    val addr = if (mn != null) MultiCoin.tronAddress(mn, MultiCoin.COINS.first { it.symbol == "USDT" }, addrIdx) else MultiCoin.tronAddressFromPriv(priv)
                                    CoinBalances.usdtBalance(addr)
                                }
                                else -> -1.0
                            }
                            synchronized(lock) { if (b >= 0) results[sym] = b }
                        } catch (_: Exception) {}
                    }
                }
                threads.forEach { it.start() }
                threads.forEach { it.join(3000) }  // 最多等3秒
                State.coinBalances = results
            }
            Api.online = true
            true
        } catch (e: Exception) {
            Api.online = false
            false
        }
    }

    fun refreshNfts(): Boolean {
        return try {
            val addr = current ?: return false
            val r = Api.nftList(addr)
            val a = r.optJSONArray("nfts")
            nfts = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }
            true
        } catch (e: Exception) { false }
    }
}
