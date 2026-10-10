package com.xiaoheicoin.wallet

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

    /** 发送 USDT(TRC20)（用助记词） */
    fun sendUSDT(mnemonic: String, coin: MultiCoin.Coin, toAddr: String, amountUsdt: Double, addressIndex: Int = 0): Pair<Boolean, String> {
        val ecKey = derivePrivateKey(mnemonic, coin, addressIndex)
        val ownerAddr = MultiCoin.tronAddress(mnemonic, coin, addressIndex)
        return sendUSDTWithKey(ecKey, ownerAddr, toAddr, amountUsdt)
    }

    /** 发送 USDT(TRC20)（用私钥hex） */
    fun sendUSDTPriv(privHex: String, toAddr: String, amountUsdt: Double): Pair<Boolean, String> {
        val ecKey = MultiCoin.privToKey(privHex)
        val ownerAddr = MultiCoin.tronAddressFromPriv(privHex)
        return sendUSDTWithKey(ecKey, ownerAddr, toAddr, amountUsdt)
    }

    private fun sendUSDTWithKey(ecKey: ECKey, ownerAddr: String, toAddr: String, amountUsdt: Double): Pair<Boolean, String> {
        try {
            val contractAddr = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t" // USDT TRC20合约
            val amountSun = (amountUsdt * 1_000_000).toLong() // USDT 6位小数

            // TRON地址转41开头hex
            fun addrToHex(addr: String): String {
                val bytes = base58Decode(addr)
                return bytes.copyOfRange(0, 21).joinToString("") { "%02x".format(it) }
            }

            // 编码transfer(address,uint256)参数
            val toHex = addrToHex(toAddr)
            val toPadded = toHex.padStart(64, '0') // 32字节
            val amountHex = amountSun.toString(16).padStart(64, '0') // 32字节
            val parameter = "a9059cbb" + toPadded + amountHex // 函数选择器+参数

            // 1. 触发合约构造交易
            val createBody = JSONObject()
                .put("owner_address", ownerAddr)
                .put("contract_address", contractAddr)
                .put("function_selector", "transfer(address,uint256)")
                .put("parameter", parameter)
                .put("fee_limit", 500_000_000) // 500 TRX手续费上限
                .put("visible", true)
                .toString()

            var txJson: JSONObject? = null
            for (node in CoinBalances.TRON_NODES) {
                val text = post("$node/wallet/triggersmartcontract", createBody) ?: continue
                txJson = JSONObject(text)
                break
            }
            if (txJson == null) return Pair(false, "构造交易失败，节点不可达")
            if (txJson.has("Error")) return Pair(false, txJson.optString("Error"))

            val transaction = txJson.optJSONObject("transaction")
            if (transaction == null) return Pair(false, "构造交易失败: ${txJson.optString("message", "未知")}")

            val rawDataHex = transaction.optString("raw_data_hex")
            val rawData = transaction.optJSONObject("raw_data")
            val txID = transaction.optString("txID")

            // 2. 签名（和TRX转账一样）
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

    /** base58解码 */
    private fun base58Decode(s: String): ByteArray {
        val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger.ZERO
        for (c in s) {
            num = num.multiply(java.math.BigInteger.valueOf(58))
                .add(java.math.BigInteger.valueOf(ALPHABET.indexOf(c).toLong()))
        }
        val bytes = num.toByteArray()
        // 去掉前导0
        val result = if (bytes.size > 1 && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
        // 补前导1
        var zeros = 0
        while (zeros < s.length && s[zeros] == '1') zeros++
        val out = ByteArray(zeros + result.size)
        System.arraycopy(result, 0, out, zeros, result.size)
        return out
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
