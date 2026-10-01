package com.xiaoheibit.wallets

import org.bitcoinj.core.ECKey
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** TRON 转账：构造交易 → 签名 → 广播 */
object Trons {

    private fun post(url: String, body: String, timeout: Int = 8000): String? {
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection)
            c.requestMethod = "POST"
            c.connectTimeout = timeout; c.readTimeout = timeout
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            c.disconnect()
            if (code in 200..299) text else { android.util.Log.e("Trons", "HTTP $code: $text"); null }
        } catch (e: Exception) { android.util.Log.e("Trons", "post error: ${e.message}"); null }
    }

    /** 发送 TRX（用助记词） */
    fun send(mnemonic: String, coin: MultiCoin.Coin, toAddr: String, amountTrx: Double, addressIndex: Int = 0): Pair<Boolean, String> {
        val ecKey = derivePrivateKey(mnemonic, coin, addressIndex)
        val ownerAddr = MultiCoin.tronAddress(mnemonic, coin, addressIndex)
        return sendWithKey(ecKey, ownerAddr, toAddr, amountTrx)
    }

    /** 发送 TRX（用私钥hex） */
    fun sendPriv(privHex: String, toAddr: String, amountTrx: Double): Pair<Boolean, String> {
        val ecKey = MultiCoin.privToKey(privHex)
        val ownerAddr = MultiCoin.tronAddressFromPriv(privHex)
        return sendWithKey(ecKey, ownerAddr, toAddr, amountTrx)
    }

    private fun sendWithKey(ecKey: ECKey, ownerAddr: String, toAddr: String, amountTrx: Double): Pair<Boolean, String> {
        try {
            val amountSun = (amountTrx * 1_000_000).toLong()

            // 1. 构造交易
            val createBody = JSONObject()
                .put("owner_address", ownerAddr)
                .put("to_address", toAddr)
                .put("amount", amountSun)
                .put("visible", true)
                .toString()

            var txJson: JSONObject? = null
            for (node in CoinBalances.TRON_NODES) {
                val text = post("$node/wallet/createtransaction", createBody) ?: continue
                txJson = JSONObject(text)
                break
            }
            if (txJson == null) return Pair(false, "构造交易失败，节点不可达")

            if (txJson.has("Error")) return Pair(false, txJson.optString("Error"))

            val rawDataHex = txJson.optString("raw_data_hex")
            val rawData = txJson.optJSONObject("raw_data")
            val txID = txJson.optString("txID")

            // 2. 签名
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(org.bouncycastle.util.encoders.Hex.decode(rawDataHex))
            val hash = org.bitcoinj.core.Sha256Hash.wrap(digest)
            val sig = ecKey.sign(hash)
            val rBytes = toBytes32(sig.r)
            val sBytes = toBytes32(sig.s)
            val myPub = ecKey.pubKeyPoint
            var recid = 0
            for (i in 0..3) {
                try {
                    val recovered = ECKey.recoverFromSignature(i, sig, hash, false)
                    if (recovered != null && recovered.pubKeyPoint.equals(myPub)) { recid = i; break }
                } catch (_: Exception) {}
            }
            val compactSig = rBytes + sBytes + byteArrayOf(recid.toByte())
            val sigHex = org.bouncycastle.util.encoders.Hex.toHexString(compactSig)

            // 3. 广播
            val broadcastBody = JSONObject()
                .put("raw_data", rawData)
                .put("signature", org.json.JSONArray().put(sigHex))
                .put("visible", true)
                .toString()

            for (node in CoinBalances.TRON_NODES) {
                val text = post("$node/wallet/broadcasttransaction", broadcastBody) ?: continue
                android.util.Log.d("Trons", "broadcast response: $text")
                val result = JSONObject(text)
                if (result.optBoolean("result", false)) {
                    return Pair(true, "已广播: ${result.optString("txid", txID)}")
                }
                val err = if (result.has("Error")) result.getString("Error")
                    else if (result.has("message")) result.getString("message")
                    else text.take(200)
                return Pair(false, err)
            }
            return Pair(false, "广播失败，节点不可达")
        } catch (e: Exception) {
            return Pair(false, e.message ?: "异常")
        }
    }

    /** 派生 TRON 私钥（ECKey，可签名），支持多地址索引 */
    private fun derivePrivateKey(mnemonic: String, coin: MultiCoin.Coin, addressIndex: Int = 0): ECKey {
        val words = mnemonic.split(" ")
        val seed = org.bitcoinj.crypto.MnemonicCode.toSeed(words, "")
        val master = org.bitcoinj.crypto.HDKeyDerivation.createMasterPrivateKey(seed)
        var key = master
        val cns = parsePath(coin.path).toMutableList()
        if (cns.isNotEmpty()) {
            cns[cns.size - 1] = org.bitcoinj.crypto.ChildNumber(addressIndex, false)
        }
        for (cn in cns) key = org.bitcoinj.crypto.HDKeyDerivation.deriveChildKey(key, cn)
        return key
    }

    private fun toBytes32(bi: java.math.BigInteger): ByteArray {
        val b = bi.toByteArray()
        val out = ByteArray(32)
        if (b.size == 33) System.arraycopy(b, 1, out, 0, 32)
        else if (b.size <= 32) System.arraycopy(b, 0, out, 32 - b.size, b.size)
        else System.arraycopy(b, b.size - 32, out, 0, 32)
        return out
    }

    private fun parsePath(path: String): List<org.bitcoinj.crypto.ChildNumber> {
        return path.split("/").filter { it != "m" && it.isNotBlank() }.map {
            val hardened = it.endsWith("'")
            val num = (if (hardened) it.removeSuffix("'") else it).toInt()
            org.bitcoinj.crypto.ChildNumber(num, hardened)
        }
    }
}
