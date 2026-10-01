package com.xiaoheibit.wallets

import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.core.ECKey
import java.math.BigInteger
import java.security.MessageDigest

/**
 * 多币种：用 bitcoinj 库做 BIP39/BIP44，大小无所谓
 */
object MultiCoin {

    data class Coin(
        val name: String,
        val symbol: String,
        val path: String,
        val chain: String,
        val isNative: Boolean = false
    )

    val COINS = listOf(
        Coin("XiaoHeiBit", "XHB", "m/44'/0'/0'/0/0", "本地私有链", true),
        Coin("TRON", "TRX", "m/44'/195'/0'/0/0", "TRON"),
        Coin("BNB Chain", "BNB", "m/44'/60'/0'/0/0", "BSC"),
        Coin("Tether USD", "USDT", "m/44'/195'/0'/0/0", "TRC20"),
    )

    /** BIP39 生成12词 */
    fun generateMnemonic(): String {
        val mc = MnemonicCode()
        val entropy = ByteArray(16)
        java.security.SecureRandom().nextBytes(entropy)
        return mc.toMnemonic(entropy).joinToString(" ")
    }

    /** 解析路径 m/44'/60'/0'/0/0 -> ChildNumber列表 */
    private fun parsePath(path: String): List<ChildNumber> {
        return path.split("/").filter { it != "m" && it.isNotBlank() }.map {
            val hardened = it.endsWith("'")
            val num = (if (hardened) it.removeSuffix("'") else it).toInt()
            ChildNumber(num, hardened)
        }
    }

    /** 派生 ECKey，支持指定地址索引（多地址） */
    private fun key(mnemonic: String, path: String, addressIndex: Int = 0): ECKey {
        val words = mnemonic.split(" ")
        val seed = MnemonicCode.toSeed(words, "")
        val master = HDKeyDerivation.createMasterPrivateKey(seed)
        var k = master
        val cns = parsePath(path).toMutableList()
        // 替换最后一位为指定的 addressIndex
        if (cns.isNotEmpty()) {
            cns[cns.size - 1] = ChildNumber(addressIndex, false)
        }
        for (cn in cns) k = HDKeyDerivation.deriveChildKey(k, cn)
        return k
    }

    /** BSC/ETH 地址：keccak256(未压缩公钥64字节)[12:] */
    fun bscAddress(mnemonic: String, coin: Coin, addressIndex: Int = 0): String {
        val ecKey = key(mnemonic, coin.path, addressIndex)
        // 未压缩公钥：04 + X(32) + Y(32) = 65字节，keccak取后64字节
        val pubPoint = ecKey.pubKeyPoint
        val x = pubPoint.xCoord.toBigInteger().toByteArray()
        val y = pubPoint.yCoord.toBigInteger().toByteArray()
        // 截取最后32字节（去掉前导符号位）
        val xb = ByteArray(32); System.arraycopy(x, maxOf(0, x.size-32), xb, maxOf(0, 32-x.size), minOf(32, x.size))
        val yb = ByteArray(32); System.arraycopy(y, maxOf(0, y.size-32), yb, maxOf(0, 32-y.size), minOf(32, y.size))
        val pub64 = xb + yb
        val h = keccak256(pub64)
        val lower = h.copyOfRange(12, 32).joinToString("") { "%02x".format(it) }
        // EIP-55 校验和
        val hash = keccak256(lower.toByteArray()).joinToString("") { "%02x".format(it) }
        val checksum = lower.mapIndexed { i, c ->
            if (hash[i].toString().toInt(16) >= 8) c.uppercaseChar() else c
        }.joinToString("")
        return "0x$checksum"
    }

    /** TRON 地址：keccak256(未压缩公钥后64字节)[12:] -> 0x41 -> checksum -> base58 */
    fun tronAddress(mnemonic: String, coin: Coin, addressIndex: Int = 0): String {
        val ecKey = key(mnemonic, coin.path, addressIndex)
        val uncompressed = ecKey.pubKeyPoint.getEncoded(false)  // 65字节: 04+X+Y
        val pub64 = uncompressed.copyOfRange(1, 65)  // 去掉04
        val h = keccak256(pub64)
        val payload = ByteArray(21)
        payload[0] = 0x41
        System.arraycopy(h, 12, payload, 1, 20)
        val check = MessageDigest.getInstance("SHA-256")
            .digest(MessageDigest.getInstance("SHA-256").digest(payload))
        return base58(payload + check.copyOfRange(0, 4))
    }

    // keccak256（bouncycastle）
    private fun keccak256(input: ByteArray): ByteArray {
        val digest = org.bouncycastle.crypto.digests.KeccakDigest(256)
        digest.update(input, 0, input.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)
        return out
    }

    private const val B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private fun base58(bytes: ByteArray): String {
        var zeros = 0
        while (zeros < bytes.size && bytes[zeros] == 0.toByte()) zeros++
        var num = BigInteger(1, bytes)
        val sb = StringBuilder()
        while (num > BigInteger.ZERO) {
            sb.append(B58[num.mod(BigInteger.valueOf(58)).toInt()])
            num /= BigInteger.valueOf(58)
        }
        repeat(zeros) { sb.append('1') }
        return sb.reverse().toString()
    }

    /** 从私钥hex派生ECKey */
    fun privToKey(privHex: String): ECKey {
        val priv = privHex.trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ECKey.fromPrivate(priv)
    }

    /** 从私钥hex派生TRON地址 */
    fun tronAddressFromPriv(privHex: String): String {
        val ecKey = privToKey(privHex)
        val uncompressed = ecKey.pubKeyPoint.getEncoded(false)
        val pub64 = uncompressed.copyOfRange(1, 65)
        val h = keccak256(pub64)
        val payload = ByteArray(21)
        payload[0] = 0x41
        System.arraycopy(h, 12, payload, 1, 20)
        val check = MessageDigest.getInstance("SHA-256")
            .digest(MessageDigest.getInstance("SHA-256").digest(payload))
        return base58(payload + check.copyOfRange(0, 4))
    }

    /** 从私钥hex派生BSC地址 */
    fun bscAddressFromPriv(privHex: String): String {
        val ecKey = privToKey(privHex)
        val pubPoint = ecKey.pubKeyPoint
        val x = pubPoint.xCoord.toBigInteger().toByteArray()
        val y = pubPoint.yCoord.toBigInteger().toByteArray()
        val xb = ByteArray(32); System.arraycopy(x, maxOf(0, x.size-32), xb, maxOf(0, 32-x.size), minOf(32, x.size))
        val yb = ByteArray(32); System.arraycopy(y, maxOf(0, y.size-32), yb, maxOf(0, 32-y.size), minOf(32, y.size))
        val pub64 = xb + yb
        val h = keccak256(pub64)
        val lower = h.copyOfRange(12, 32).joinToString("") { "%02x".format(it) }
        val hash = keccak256(lower.toByteArray()).joinToString("") { "%02x".format(it) }
        val checksum = lower.mapIndexed { i, c ->
            if (hash[i].toString().toInt(16) >= 8) c.uppercaseChar() else c
        }.joinToString("")
        return "0x$checksum"
    }
}
