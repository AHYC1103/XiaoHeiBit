package com.xiaoheicoin.wallet

import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64

/** WalletConnect v2 核心：Pairing / Session / 请求处理 */
object WCCore {

    data class Session(
        val topic: String,
        val symKey: ByteArray,
        val peerMeta: JSONObject,
        val accounts: List<String>
    )

    private var pairingTopic = ""
    private var pairingSymKey = ByteArray(0)
    private var responderPriv = ByteArray(0)
    private var responderPub = ByteArray(0)
    private var pendingProposalId: Long = 0
    var lastError = ""
    private val sessions = HashMap<String, Session>()
    private var onSessionProposal: ((JSONObject) -> Unit)? = null
    private var onRequest: ((String, JSONObject) -> Unit)? = null
    private var onPairingReady: (() -> Unit)? = null
    private var onStatus: ((String) -> Unit)? = null

    private fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun setOnStatus(cb: (String) -> Unit) { onStatus = cb }
    private fun status(s: String) { LogUtil.d("WCCore", s); onStatus?.invoke(s) }

    /** 连接 DApp 的 pairing URI（钱包扫码/粘贴后调用） */
    fun connectPairing(uri: String): Boolean {
        try {
            lastError = ""
            val cleanUri = uri.trim()
            LogUtil.d("WCCore", "connectPairing uri=$cleanUri")
            LogUtil.d("WCCore", "URI长度=${cleanUri.length}")
            if (!cleanUri.startsWith("wc:")) { lastError = "缺少 wc: 前缀"; return false }
            val withoutPrefix = cleanUri.removePrefix("wc:")
            LogUtil.d("WCCore", "去前缀后=$withoutPrefix")
            val atIdx = withoutPrefix.indexOf('@')
            if (atIdx < 0) { lastError = "缺少 @ 符号"; return false }
            pairingTopic = withoutPrefix.substring(0, atIdx)
            LogUtil.d("WCCore", "topic=$pairingTopic (len=${pairingTopic.length})")
            val qIdx = withoutPrefix.indexOf('?')
            if (qIdx < 0) { lastError = "缺少 ? 符号"; return false }
            val query = withoutPrefix.substring(qIdx + 1)
            LogUtil.d("WCCore", "query=$query")
            val params = query.split('&').associate {
                val kv = it.split('='); kv[0] to kv.getOrElse(1) { "" }
            }
            LogUtil.d("WCCore", "params keys=${params.keys}")
            val symKeyHex = params["symKey"]
            if (symKeyHex == null) { lastError = "缺少 symKey 参数, params=$params"; return false }
            if (symKeyHex.length != 64) { lastError = "symKey 长度不对: ${symKeyHex.length}"; return false }
            if (!symKeyHex.matches(Regex("[0-9a-fA-F]+"))) { lastError = "symKey 含非 hex 字符"; return false }
            pairingSymKey = WCCrypto.hexToBytes(symKeyHex)
            LogUtil.d("WCCore", "symKey=${WCCrypto.bytesToHex(pairingSymKey)} (len=${pairingSymKey.size})")
            status("解析成功，topic=${pairingTopic.take(8)}...")

            // 生成 responder X25519 密钥对
            val kp = WCCrypto.generateKeyPair()
            responderPriv = kp.privateKey
            responderPub = kp.publicKey
            LogUtil.d("WCCore", "responderPub=${WCCrypto.bytesToHex(responderPub)} (len=${responderPub.size})")
            LogUtil.d("WCCore", "responderPriv=${WCCrypto.bytesToHex(responderPriv)} (len=${responderPriv.size})")
            status("生成密钥对完成")

            WCRelay.subscribe(pairingTopic, { encryptedMsg ->
                status("收到 pairing 消息")
                handlePairingMessage(encryptedMsg)
            }, onSubscribed = {
                status("订阅确认，发送 pairing 激活...")
                // 发送 pairing 激活消息，通知 DApp 钱包已在线
                val approveMsg = JSONObject()
                    .put("id", System.currentTimeMillis())
                    .put("jsonrpc", "2.0")
                    .put("method", "wc_pairingApprove")
                    .put("params", JSONObject()
                        .put("relay", JSONObject().put("protocol", "irn"))
                        .put("responderPublicKey", WCCrypto.bytesToHex(responderPub)))
                val encrypted = WCCrypto.encrypt(pairingSymKey, approveMsg.toString().toByteArray())
                LogUtil.d("WCCore", "pairingApprove明文=${approveMsg}")
                LogUtil.d("WCCore", "pairingApprove密文=${WCCrypto.bytesToHex(encrypted)} (len=${encrypted.size})")
                WCRelay.publish(pairingTopic, b64url(encrypted), ttl = 300, tag = 1000)
                status("已发送 pairing 激活，等待 DApp 会话请求...")
            })
            status("已订阅 pairing topic，等待确认...")

            onPairingReady?.invoke()
            return true
        } catch (e: Exception) {
            lastError = "异常: ${e.message}"
            LogUtil.e("WCCore", "connectPairing error: ${e.message}")
            return false
        }
    }

    fun setOnPairingReady(cb: () -> Unit) { onPairingReady = cb }

    fun setOnSessionProposal(cb: (JSONObject) -> Unit) { onSessionProposal = cb }
    fun setOnRequest(cb: (String, JSONObject) -> Unit) { onRequest = cb }

    /** 处理 pairing 通道的消息（session_proposal） */
    private fun handlePairingMessage(encryptedHex: String) {
        try {
            status("解密 pairing 消息...")
            val encrypted = WCCrypto.hexToBytes(encryptedHex)
            LogUtil.d("WCCore", "pairing msg: hexLen=${encryptedHex.length} bytes=${encrypted.size} symKeyLen=${pairingSymKey.size}")
            val decrypted = WCCrypto.decrypt(pairingSymKey, encrypted)
            val json = JSONObject(String(decrypted))
            val method = json.optString("method")
            status("收到 method=$method")
            LogUtil.d("WCCore", "pairing decrypted: ${String(decrypted).take(200)}")
            if (method == "wc_sessionPropose") {
                pendingProposalId = json.optLong("id")
                val params = json.getJSONObject("params")
                onSessionProposal?.invoke(params)
            }
        } catch (e: Exception) {
            status("解密失败: ${e.message}")
            LogUtil.e("WCCore", "pairing msg error: ${e.message}")
        }
    }

    /** 批准会话 */
    fun approveSession(proposal: JSONObject) {
        try {
            LogUtil.d("WCCore", "approveSession proposal=${proposal}")
            val relayProtocol = proposal.optJSONObject("relay")?.optString("protocol") ?: "irn"
            val proposerPubKey = proposal.getJSONObject("proposer").getString("publicKey")

            // session topic(Topic B) = sha256(pairing responderPub)，DApp 在 pairingApprove 时已知 responderPub，已提前订阅
            val sessionTopic = WCCrypto.sha256Hex(responderPub)
            val sessionSymKey = WCCrypto.deriveSymKey(responderPriv, WCCrypto.hexToBytes(proposerPubKey))
            LogUtil.d("WCCore", "sessionTopic(Topic B)=$sessionTopic responderPub=${WCCrypto.bytesToHex(responderPub)}")

            // 构建 namespaces：根据 DApp 请求的链返回对应地址
            val namespaces = JSONObject()
            val allAccounts = mutableListOf<String>()
            val requested = JSONObject()
            proposal.optJSONObject("requiredNamespaces")?.let { req ->
                for (k in req.keys()) requested.put(k, req.get(k))
            }
            proposal.optJSONObject("optionalNamespaces")?.let { opt ->
                for (k in opt.keys()) if (!requested.has(k)) requested.put(k, opt.get(k))
            }
            for (nsKey in requested.keys()) {
                val nsObj = requested.getJSONObject(nsKey)
                val chains = nsObj.optJSONArray("chains") ?: JSONArray()
                val methods = nsObj.optJSONArray("methods") ?: JSONArray()
                val events = nsObj.optJSONArray("events") ?: JSONArray()
                val accounts = JSONArray()
                for (i in 0 until chains.length()) {
                    val chain = chains.getString(i)
                    when {
                        chain.startsWith("eip155:") -> {
                            val acc = "eip155:56:${bscAddress()}"
                            accounts.put(acc)
                            allAccounts.add(acc)
                        }
                        chain.startsWith("tron:") -> {
                            val acc = "tron:0x2b6653dc:${tronAddress()}"
                            accounts.put(acc)
                            allAccounts.add(acc)
                        }
                    }
                }
                if (accounts.length() > 0) {
                    namespaces.put(nsKey, JSONObject()
                        .put("accounts", accounts)
                        .put("methods", methods)
                        .put("events", events)
                        .put("chains", chains))
                }
            }
            if (namespaces.length() == 0) {
                val acc = "eip155:56:${bscAddress()}"
                allAccounts.add(acc)
                namespaces.put("eip155", JSONObject()
                    .put("accounts", JSONArray(listOf(acc)))
                    .put("methods", JSONArray(listOf("eth_sendTransaction","eth_sign","personal_sign","eth_signTypedData","eth_signTypedData_v4")))
                    .put("events", JSONArray(listOf("chainChanged","accountsChanged")))
                    .put("chains", JSONArray(listOf("eip155:56"))))
            }

            // 第一步：回复 wc_sessionPropose 的 JSON-RPC response（result=true）到 pairing topic，tag=1101
            val proposeResponse = JSONObject()
                .put("id", pendingProposalId)
                .put("jsonrpc", "2.0")
                .put("result", true)
            val encResp = WCCrypto.encrypt(pairingSymKey, proposeResponse.toString().toByteArray())
            WCRelay.publish(pairingTopic, b64url(encResp), ttl = 300, tag = 1101)
            LogUtil.d("WCCore", "已回复 sessionPropose response id=$pendingProposalId")

            // 第二步：wc_sessionSettle 消息（JSON-RPC request），发到 session topic，用 sessionSymKey 加密
            val settleMsg = JSONObject()
                .put("id", System.currentTimeMillis())
                .put("jsonrpc", "2.0")
                .put("method", "wc_sessionSettle")
                .put("params", JSONObject()
                    .put("relay", JSONObject().put("protocol", relayProtocol))
                    .put("controller", JSONObject()
                        .put("publicKey", WCCrypto.bytesToHex(responderPub))
                        .put("metadata", JSONObject()
                            .put("name", "XiaoHeiCoin Wallet")
                            .put("description", "XiaoHeiCoin 多币种钱包")
                            .put("url", "https://github.com/AHYC1103/XiaoHeiBit")
                            .put("icons", JSONArray(listOf("https://aka.doubaocdn.com/s/placeholder")))))
                    .put("namespaces", namespaces)
                    .put("expiry", (System.currentTimeMillis() / 1000 + 7 * 24 * 3600).toInt()))

            // 第三步：订阅 session topic，确认后发送 sessionSettle
            WCRelay.subscribe(sessionTopic, onMessage = { encryptedMsg ->
                handleSessionMessage(sessionTopic, sessionSymKey, encryptedMsg)
            }, onSubscribed = {
                val encrypted = WCCrypto.encrypt(sessionSymKey, settleMsg.toString().toByteArray())
                LogUtil.d("WCCore", "sessionSettle明文=${settleMsg}")
                WCRelay.publish(sessionTopic, b64url(encrypted), ttl = 300, tag = 1102)
                LogUtil.d("WCCore", "sessionSettle已发送到 session topic=$sessionTopic")
            })

            // 保存会话
            sessions[sessionTopic] = Session(
                topic = sessionTopic,
                symKey = sessionSymKey,
                peerMeta = proposal.getJSONObject("proposer").getJSONObject("metadata"),
                accounts = allAccounts
            )
        } catch (e: Exception) {
            LogUtil.e("WCCore", "approve error: ${e.message}")
        }
    }

    /** 拒绝会话 */
    fun rejectSession(proposal: JSONObject, reason: String = "User rejected") {
        // 简化：不发送 reject，直接忽略
    }

    /** 处理 session 通道的请求 */
    private fun handleSessionMessage(topic: String, symKey: ByteArray, encryptedHex: String) {
        try {
            val encrypted = WCCrypto.hexToBytes(encryptedHex)
            val decrypted = WCCrypto.decrypt(symKey, encrypted)
            val json = JSONObject(String(decrypted))
            val method = json.optString("method")
            if (method == "wc_sessionRequest") {
                val params = json.getJSONObject("params")
                val request = params.getJSONObject("request")
                val reqMethod = request.getString("method")
                val reqParams = request.optJSONArray("params") ?: JSONArray()
                val reqId = json.optLong("id")
                onRequest?.invoke(topic, JSONObject()
                    .put("id", reqId)
                    .put("method", reqMethod)
                    .put("params", reqParams)
                    .put("topic", topic))
            }
        } catch (e: Exception) {
            LogUtil.e("WCCore", "session msg error: ${e.message}")
        }
    }

    /** 响应请求 */
    fun respond(topic: String, id: Long, result: String?, error: String?) {
        val session = sessions[topic] ?: return
        val response = JSONObject()
            .put("id", id)
            .put("jsonrpc", "2.0")
        if (error != null) {
            response.put("error", JSONObject().put("code", 5000).put("message", error))
        } else {
            response.put("result", result)
        }
        val encrypted = WCCrypto.encrypt(session.symKey, response.toString().toByteArray())
        WCRelay.respond(topic, b64url(encrypted))
    }

    fun getSessions() = sessions.values.toList()

    fun disconnect(topic: String) {
        sessions.remove(topic)
    }

    private fun bscAddress(): String {
        val mn = WalletStore.current()?.mnemonic ?: return "0x0000000000000000000000000000000000000000"
        val coin = MultiCoin.COINS.first { it.symbol == "BNB" }
        return MultiCoin.bscAddress(mn, coin)
    }

    private fun tronAddress(): String {
        val mn = WalletStore.current()?.mnemonic ?: return "T000000000000000000000000000000000"
        val coin = MultiCoin.COINS.first { it.symbol == "TRX" }
        return MultiCoin.tronAddress(mn, coin)
    }
}
