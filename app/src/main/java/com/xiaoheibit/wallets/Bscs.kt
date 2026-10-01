package com.xiaoheibit.wallets

import org.bitcoinj.core.ECKey
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/** BSC 转账：RLP 编码 → EIP-155 签名 → eth_sendRawTransaction 广播 */
object Bscs {

    const val CHAIN_ID = 56L
    private const val GAS_LIMIT = 21000L

    private fun rpc(node: String, method: String, params: List<Any>): JSONObject? {
        return try {
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method)
                .put("params", org.json.JSONArray(params.map {
                    when (it) {
                        is String -> it
                        is Long -> it
                        is BigInteger -> it
                        else -> it
                    }
                }))
                .toString()
            val c = (URL(node).openConnection() as HttpURLConnection)
            c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            JSONObject(text)
        } catch (e: Exception) { null }
    }

    fun send(mnemonic: String, coin: MultiCoin.Coin, toAddr: String, amountBnb: Double): Pair<Boolean, String> {
        val ecKey = derivePrivateKey(mnemonic, coin)
        val ownerAddr = "0x" + org.bouncycastle.util.encoders.Hex.toHexString(
            keccak256(ecKey.pubKeyPoint.getEncoded(false).copyOfRange(1, 65)).copyOfRange(12, 32))
        return sendWithKey(ecKey, ownerAddr, toAddr, amountBnb)
    }

    fun sendPriv(privHex: String, toAddr: String, amountBnb: Double): Pair<Boolean, String> {
        val ecKey = MultiCoin.privToKey(privHex)
        val ownerAddr = MultiCoin.bscAddressFromPriv(privHex)
        return sendWithKey(ecKey, ownerAddr, toAddr, amountBnb)
    }

    private fun sendWithKey(ecKey: ECKey, ownerAddr: String, toAddr: String, amountBnb: Double): Pair<Boolean, String> {
        try {
            var nodeUsed = ""
            var nonce: BigInteger? = null
            var gasPrice: BigInteger? = null

            for (node in CoinBalances.BSC_NODES) {
                val nonceRes = rpc(node, "eth_getTransactionCount", listOf(ownerAddr, "pending"))
                val gasRes = rpc(node, "eth_gasPrice", listOf())
                if (nonceRes != null && gasRes != null) {
                    nonce = BigInteger(nonceRes.getString("result").removePrefix("0x").ifEmpty { "0" }, 16)
                    gasPrice = BigInteger(gasRes.getString("result").removePrefix("0x"), 16)
                    nodeUsed = node
                    break
                }
            }
            if (nonce == null || gasPrice == null) return Pair(false, "节点不可达")

            val valueWei = BigInteger.valueOf((amountBnb * 1e18).toLong())
            val toBytes = org.bouncycastle.util.encoders.Hex.decode(toAddr.removePrefix("0x"))

            val signingItems = listOf(
                rlpEncode(bi(nonce)),
                rlpEncode(bi(gasPrice)),
                rlpEncode(bi(BigInteger.valueOf(GAS_LIMIT))),
                rlpEncode(toBytes),
                rlpEncode(bi(valueWei)),
                rlpEncode(ByteArray(0)),
                rlpEncode(bi(BigInteger.valueOf(CHAIN_ID))),
                rlpEncode(ByteArray(0)),
                rlpEncode(ByteArray(0)),
            )
            val signingRlp = rlpEncodeList(signingItems)
            val hash = keccak256(signingRlp)

            val sig = ecKey.sign(org.bitcoinj.core.Sha256Hash.wrap(hash))
            val rBytes = toBytes32(sig.r)
            val sBytes = toBytes32(sig.s)

            val myPub = ecKey.pubKeyPoint
            var recid = 0
            for (i in 0..3) {
                try {
                    val recovered = ECKey.recoverFromSignature(i, sig,
                        org.bitcoinj.core.Sha256Hash.wrap(hash), false)
                    if (recovered != null && recovered.pubKeyPoint.equals(myPub)) { recid = i; break }
                } catch (_: Exception) {}
            }
            val v = CHAIN_ID * 2 + 35 + recid

            val signedItems = listOf(
                rlpEncode(bi(nonce)),
                rlpEncode(bi(gasPrice)),
                rlpEncode(bi(BigInteger.valueOf(GAS_LIMIT))),
                rlpEncode(toBytes),
                rlpEncode(bi(valueWei)),
                rlpEncode(ByteArray(0)),
                rlpEncode(bi(BigInteger.valueOf(v))),
                rlpEncode(rBytes),
                rlpEncode(sBytes),
            )
            val rawTx = rlpEncodeList(signedItems)
            val rawHex = "0x" + org.bouncycastle.util.encoders.Hex.toHexString(rawTx)

            val result = rpc(nodeUsed, "eth_sendRawTransaction", listOf(rawHex))
                ?: return Pair(false, "广播失败")
            val res = result.opt("result")
            val err = result.optJSONObject("error")
            if (err != null) return Pair(false, err.optString("message"))
            return Pair(true, "已广播: $res")
        } catch (e: Exception) {
            return Pair(false, e.message ?: "异常")
        }
    }

    // ===== RLP 编码 =====
    private fun bi(b: BigInteger): ByteArray {
        if (b == BigInteger.ZERO) return ByteArray(0)
        val arr = b.toByteArray()
        return if (arr.size > 1 && arr[0] == 0.toByte()) arr.copyOfRange(1, arr.size) else arr
    }

    private fun rlpEncode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf(0x80.toByte())
        return when {
            data.size == 1 && (data[0].toInt() and 0xFF) < 0x80 -> data
            data.size <= 55 -> byteArrayOf((0x80 + data.size).toByte()) + data
            else -> {
                val len = BigInteger.valueOf(data.size.toLong()).toByteArray()
                byteArrayOf((0xb7 + len.size).toByte()) + len + data
            }
        }
    }

    private fun rlpEncodeList(items: List<ByteArray>): ByteArray {
        val payload = items.fold(ByteArray(0)) { acc, item -> acc + item }
        return when {
            payload.size <= 55 -> byteArrayOf((0xc0 + payload.size).toByte()) + payload
            else -> {
                val len = BigInteger.valueOf(payload.size.toLong()).toByteArray()
                byteArrayOf((0xf7 + len.size).toByte()) + len + payload
            }
        }
    }

    // ===== Keccak256 =====
    private fun keccak256(data: ByteArray): ByteArray {
        val digest = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256()
        return digest.digest(data)
    }

    private fun toBytes32(bi: BigInteger): ByteArray {
        val b = bi.toByteArray()
        val out = ByteArray(32)
        if (b.size == 33) System.arraycopy(b, 1, out, 0, 32)
        else if (b.size <= 32) System.arraycopy(b, 0, out, 32 - b.size, b.size)
        else System.arraycopy(b, b.size - 32, out, 0, 32)
        return out
    }

    fun derivePrivateKey(mnemonic: String, coin: MultiCoin.Coin): ECKey {
        val words = mnemonic.split(" ")
        val seed = org.bitcoinj.crypto.MnemonicCode.toSeed(words, "")
        val master = org.bitcoinj.crypto.HDKeyDerivation.createMasterPrivateKey(seed)
        var key = master
        for (cn in parsePath(coin.path)) key = org.bitcoinj.crypto.HDKeyDerivation.deriveChildKey(key, cn)
        return key
    }

    private fun parsePath(path: String): List<org.bitcoinj.crypto.ChildNumber> {
        return path.split("/").filter { it != "m" && it.isNotBlank() }.map {
            val hardened = it.endsWith("'")
            val num = (if (hardened) it.removeSuffix("'") else it).toInt()
            org.bitcoinj.crypto.ChildNumber(num, hardened)
        }
    }
}
