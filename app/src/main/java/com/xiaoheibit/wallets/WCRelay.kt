package com.xiaoheibit.wallets

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** WalletConnect v2 Relay 客户端：OkHttp WebSocket + JWT 认证 */
object WCRelay {

    var projectId = "1eed5af45e42347825df98b5e1a2dc28"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var ws: WebSocket? = null
    private val subscriptions = ConcurrentHashMap<String, (String) -> Unit>()
    private var connected = false
    private var msgId = 1L
    var onRawMessage: ((String) -> Unit)? = null

    private fun nextId(): Long = System.currentTimeMillis() + (Math.random() * 10000).toLong()

    private val random = SecureRandom()
    private var clientPriv: ByteArray? = null
    private var clientPub: ByteArray? = null

    private fun ensureKeyPair() {
        if (clientPriv != null) return
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        clientPriv = (pair.private as Ed25519PrivateKeyParameters).encoded
        clientPub = (pair.public as Ed25519PublicKeyParameters).encoded
    }

    private fun generateJwt(): String {
        ensureKeyPair()
        val now = System.currentTimeMillis() / 1000
        val header = JSONObject().put("alg", "EdDSA").put("typ", "JWT")
        val payload = JSONObject()
            .put("iss", didKey(clientPub!!))
            .put("sub", projectId)
            .put("aud", "wss://relay.walletconnect.com")
            .put("iat", now)
            .put("exp", now + 7 * 24 * 3600)
        val headerB64 = base64url(header.toString().toByteArray())
        val payloadB64 = base64url(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(clientPriv, 0))
        signer.update(signingInput.toByteArray(), 0, signingInput.length)
        val sig = signer.generateSignature()
        val sigB64 = base64url(sig)
        return "$signingInput.$sigB64"
    }

    private fun base64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun didKey(pubKey: ByteArray): String {
        // multicodec Ed25519 pubkey prefix = 0xed 0x01, then 32-byte key
        val multicodec = byteArrayOf(0xed.toByte(), 0x01) + pubKey
        val b58 = org.bitcoinj.core.Base58.encode(multicodec)
        return "did:key:z$b58"
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(hex[(b.toInt() and 0xF0) shr 4])
            sb.append(hex[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    fun connect(onOpen: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (connected) { LogUtil.d("WCRelay", "已连接，跳过"); onOpen(); return }
        val jwt = generateJwt()
        val uri = "wss://relay.walletconnect.com/?projectId=$projectId&ua=XiaoHeiBit/1.0&auth=$jwt"
        LogUtil.d("WCRelay", "connect uri=$uri")
        LogUtil.d("WCRelay", "jwt=$jwt")
        val request = Request.Builder()
            .url(uri)
            .header("User-Agent", "XiaoHeiBit/1.0")
            .build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                LogUtil.d("WCRelay", "WS onOpen code=${response.code}")
                onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    LogUtil.d("WCRelay", "WS recv full=$text")
                    onRawMessage?.invoke(text.take(300))
                    val json = JSONObject(text)
                    val method = json.optString("method")
                    if (method == "irn_subscription") {
                        val params = json.getJSONObject("params")
                        val subId = params.optString("id")
                        val dataObj = params.getJSONObject("data")
                        val messageB64 = dataObj.getString("message")
                        val topic = dataObj.getString("topic")
                        LogUtil.d("WCRelay", "recv subscription subId=$subId topic=$topic msgB64Len=${messageB64.length}")
                        // relay 推送的 message 是 base64 编码，转成 hex 传给上层
                        val messageBytes = Base64.decode(messageB64, Base64.DEFAULT)
                        val messageHex = bytesToHex(messageBytes)
                        LogUtil.d("WCRelay", "recv msg hex=$messageHex")
                        subscriptions[topic]?.invoke(messageHex)
                    } else {
                        LogUtil.d("WCRelay", "recv method=$method text=$text")
                        // 处理 subscribe 成功响应
                        val respId = json.optLong("id")
                        if (json.has("result") && pendingSubscribe.containsKey(respId)) {
                            LogUtil.d("WCRelay", "subscribe confirmed id=$respId")
                            pendingSubscribe.remove(respId)?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e("WCRelay", "onMessage error: ${e.message} text=$text")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                val body = try { response?.body?.string() ?: "no body" } catch (_: Exception) { "read error" }
                val code = response?.code ?: -1
                val headers = response?.headers?.toString() ?: "no headers"
                LogUtil.e("WCRelay", "WS fail code=$code msg=${t.message} body=$body headers=$headers")
                onError("HTTP $code: ${t.message ?: ""} | $body")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                LogUtil.d("WCRelay", "WS onClosed code=$code reason=$reason")
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "bye")
        ws = null
        connected = false
    }

    fun isConnected() = connected

    private val pendingSubscribe = ConcurrentHashMap<Long, () -> Unit>()

    fun subscribe(topic: String, onMessage: (String) -> Unit, onSubscribed: (() -> Unit)? = null) {
        subscriptions[topic] = onMessage
        val id = nextId()
        if (onSubscribed != null) pendingSubscribe[id] = onSubscribed
        val msg = JSONObject()
            .put("id", id)
            .put("jsonrpc", "2.0")
            .put("method", "irn_subscribe")
            .put("params", JSONObject().put("topic", topic))
        LogUtil.d("WCRelay", "subscribe send=$msg")
        ws?.send(msg.toString())
        LogUtil.d("WCRelay", "subscribe id=$id topic=$topic")
    }

    fun publish(topic: String, message: String, ttl: Int = 300, tag: Int = 1100) {
        val id = nextId()
        val msg = JSONObject()
            .put("id", id)
            .put("jsonrpc", "2.0")
            .put("method", "irn_publish")
            .put("params", JSONObject()
                .put("topic", topic)
                .put("message", message)
                .put("ttl", ttl)
                .put("tag", tag))
        LogUtil.d("WCRelay", "publish send=${msg}")
        ws?.send(msg.toString())
        LogUtil.d("WCRelay", "publish id=$id topic=$topic tag=$tag msgLen=${message.length}")
    }

    fun respond(topic: String, message: String) {
        val id = nextId()
        val msg = JSONObject()
            .put("id", id)
            .put("jsonrpc", "2.0")
            .put("method", "irn_publish")
            .put("params", JSONObject()
                .put("topic", topic)
                .put("message", message)
                .put("ttl", 300)
                .put("tag", 1101))
        ws?.send(msg.toString())
    }
}
