package com.xiaoheicoin.wallet

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** WalletConnect v2 加密工具：X25519 + HKDF + ChaCha20-Poly1305 (RFC 8439) */
object WCCrypto {

    private val random = SecureRandom()

    data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        val priv = (pair.private as X25519PrivateKeyParameters).encoded
        val pub = (pair.public as X25519PublicKeyParameters).encoded
        return KeyPair(priv, pub)
    }

    fun deriveSymKey(selfPriv: ByteArray, peerPub: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(selfPriv, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPub, 0), shared, 0)
        return hkdf(shared, "walletconnect-".toByteArray(), 32)
    }

    fun randomBytes(len: Int): ByteArray {
        val b = ByteArray(len)
        random.nextBytes(b)
        return b
    }

    fun randomHex(len: Int): String = bytesToHex(randomBytes(len))

    /** RFC 8439 自测，返回 "PASS" 或错误信息 */
    fun selfTest(): String {
        return try {
            val key = hexToBytes("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
            val nonce = hexToBytes("070000004041424344454647")
            val plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".toByteArray()
            val aad = hexToBytes("50515253c0c1c2c3c4c5c6c7")
            // 用 BC ChaCha20Poly1305 加密（指定 nonce）
            val cipher = ChaCha20Poly1305()
            cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
            val output = ByteArray(cipher.getOutputSize(plaintext.size))
            val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
            cipher.doFinal(output, len)
            val ct = output.copyOfRange(0, plaintext.size)
            val tag = output.copyOfRange(plaintext.size, output.size)
            val expectedCt = hexToBytes("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d63dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692ddbd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc3ff4def08e4b7a9de576d26586cec64b6116")
            val expectedTag = hexToBytes("1ae10b594f09e26a7e902ecbd0600691")
            val ctOk = ct.contentEquals(expectedCt)
            val tagOk = tag.contentEquals(expectedTag)
            LogUtil.d("WCCrypto", "selfTest actualTag=${bytesToHex(tag)} expectedTag=${bytesToHex(expectedTag)}")
            // 解密自测
            val decrypted = decrypt(key, nonce + output, aad)
            val decOk = decrypted.contentEquals(plaintext)
            if (ctOk && tagOk && decOk) "PASS" else "FAIL ct=$ctOk tag=$tagOk dec=$decOk"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** ChaCha20-Poly1305 加密，返回 [1字节版本0x00] + iv(12) + ciphertext + tag(16) */
    fun encrypt(symKey: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val iv = randomBytes(12)
        LogUtil.d("WCCrypto", "encrypt: key=${bytesToHex(symKey)} iv=${bytesToHex(iv)} aadLen=${aad.size} ptLen=${plaintext.size}")
        LogUtil.d("WCCrypto", "encrypt: plaintext=${String(plaintext)}")
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(symKey), 128, iv, aad))
        val output = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, output, 0)
        cipher.doFinal(output, len)
        // WalletConnect v2 消息格式: [1字节版本号0x00] + [12字节iv] + [ciphertext] + [16字节tag]
        val result = byteArrayOf(0x00) + iv + output
        LogUtil.d("WCCrypto", "encrypt: result=${bytesToHex(result)} (len=${result.size})")
        return result
    }

    /** ChaCha20-Poly1305 解密，自动跳过1字节版本号 */
    fun decrypt(symKey: ByteArray, data: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        if (data.size < 29) throw Exception("data too short: ${data.size}")
        // 跳过 1 字节版本号
        var offset = 0
        if (data[0].toInt() == 0) offset = 1
        val iv = data.copyOfRange(offset, offset + 12)
        val ct = data.copyOfRange(offset + 12, data.size)
        LogUtil.d("WCCrypto", "decrypt: key=${bytesToHex(symKey)} iv=${bytesToHex(iv)} aadLen=${aad.size} ctLen=${ct.size}")
        LogUtil.d("WCCrypto", "decrypt: data=${bytesToHex(data)}")
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(symKey), 128, iv, aad))
        val output = ByteArray(cipher.getOutputSize(ct.size))
        val len = cipher.processBytes(ct, 0, ct.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        val result = output.copyOfRange(0, len + finalLen)
        LogUtil.d("WCCrypto", "decrypt: plaintext=${String(result)}")
        return result
    }

    private fun hkdf(ikm: ByteArray, info: ByteArray, len: Int): ByteArray {
        val salt = ByteArray(32) // 32 bytes of zeros
        val prk = hmacSha256(salt, ikm)
        var t = ByteArray(0)
        var okm = ByteArray(0)
        var i = 1
        while (okm.size < len) {
            t = hmacSha256(prk, t + info + i.toByte())
            okm += t
            i++
        }
        return okm.copyOfRange(0, len)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(hex[(b.toInt() and 0xF0) shr 4])
            sb.append(hex[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val h = if (hex.startsWith("0x")) hex.removePrefix("0x") else hex
        val len = h.length / 2
        val out = ByteArray(len)
        for (i in 0 until len) {
            out[i] = ((Character.digit(h[i * 2], 16) shl 4) or Character.digit(h[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    /** SHA-256 哈希，返回 hex */
    fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return bytesToHex(md.digest(data))
    }
}
