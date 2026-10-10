package com.xiaoheicoin.wallet

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/** DApp 浏览器：内置 WebView + 注入 window.ethereum（EIP-1193） */
object DAppPages {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var titleTv: TextView
    private var currentUrl = "https://pancakeswap.finance"

    @SuppressLint("SetJavaScriptEnabled")
    fun build(): View {
        val ctx = Nav.act
        val sv = LinearLayout(ctx); sv.orientation = LinearLayout.VERTICAL

        // 顶部地址栏
        val bar = LinearLayout(ctx); bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
        bar.setBackgroundColor(C.CARD)
        val back = tv(ctx, "‹", 22f, C.TXT).apply {
            setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
            setOnClickListener { Nav.act.runOnUiThread { if (webView.canGoBack()) webView.goBack() } }
        }
        bar.addView(back)
        urlInput = EditText(ctx).apply {
            hint = "输入网址"
            setTextColor(C.TXT)
            setHintTextColor(C.SUB)
            textSize = 13f
            setBackgroundColor(C.CARD2)
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            setSingleLine()
            setText(currentUrl)
        }
        val ilp = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8.dp(); marginEnd = 8.dp() }
        urlInput.layoutParams = ilp
        bar.addView(urlInput)
        val go = tv(ctx, "GO", 13f, C.GREEN).apply {
            setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
            setOnClickListener { loadUrl(urlInput.text.toString().trim()) }
        }
        bar.addView(go)
        sv.addView(bar)

        // 标题
        titleTv = tv(ctx, "DApp 浏览器", 12f, C.SUB).apply {
            setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
            setBackgroundColor(C.CARD)
        }
        sv.addView(titleTv)

        // WebView
        webView = WebView(ctx).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = settings.userAgentString + " XiaoHeiCoinWallet"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { urlInput.setText(it); currentUrl = it }
                    injectEthereum()
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    titleTv.text = view?.title ?: url
                    injectEthereum()
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(EthereumBridge(), "XHBAndroid")
        }
        val wlp = LinearLayout.LayoutParams(-1, 0, 1f)
        webView.layoutParams = wlp
        sv.addView(webView)

        webView.loadUrl(currentUrl)
        return sv
    }

    private fun loadUrl(url: String) {
        var u = url
        if (!u.startsWith("http")) u = "https://$u"
        currentUrl = u
        LogUtil.d("DAppPages", "loadUrl: $u")
        Nav.act.runOnUiThread { webView.loadUrl(u) }
    }

    /** 注入 window.ethereum（EIP-1193 标准） */
    private fun injectEthereum() {
        val js = """
        (function() {
            if (window.ethereum && window.ethereum.isXiaoHeiCoin) return;
            var callbacks = {};
            var cbId = 0;
            var eth = {
                isMetaMask: true,
                isXiaoHeiCoin: true,
                chainId: '0x38',
                networkVersion: '56',
                selectedAddress: null,
                _listeners: {},
                request: function(args) {
                    return new Promise(function(resolve, reject) {
                        var id = ++cbId;
                        callbacks[id] = {resolve: resolve, reject: reject};
                        try {
                            XHBAndroid.request(args.method, JSON.stringify(args.params||[]), id);
                        } catch(e) { reject(e); }
                    });
                },
                on: function(event, cb) { (this._listeners[event]=this._listeners[event]||[]).push(cb); },
                removeListener: function(event, cb) { if(this._listeners[event]) this._listeners[event]=this._listeners[event].filter(function(f){return f!==cb;}); },
                isConnected: function() { return true; },
                _emit: function(event, data) { (this._listeners[event]||[]).forEach(function(cb){cb(data);}); }
            };
            window.ethereum = eth;
            window.__xhbCallback = function(id, result, error) {
                var cb = callbacks[id];
                if (!cb) return;
                delete callbacks[id];
                if (error) cb.reject(new Error(error));
                else cb.resolve(result);
            };
            window.__xhbAccounts = function(addr) {
                eth.selectedAddress = addr;
                eth._emit('accountsChanged', [addr]);
            };
            // EIP-6963
            window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {detail:{info:{uuid:'xhb-wallet',name:'XiaoHeiCoin',icon:''},provider:eth}}));
            window.dispatchEvent(new Event('ethereum#initialized'));
        })();
        """.trimIndent()
        Nav.act.runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    /** JS → Java 桥 */
    class EthereumBridge {
        @JavascriptInterface
        fun request(method: String, paramsJson: String, callbackId: Int) {
            Nav.act.runOnUiThread {
                try {
                    handleRequest(method, JSONArray(paramsJson), callbackId)
                } catch (e: Exception) {
                    jsCallback(callbackId, null, e.message ?: "error")
                }
            }
        }
    }

    private fun handleRequest(method: String, params: JSONArray, cbId: Int) {
        LogUtil.d("DAppPages", "handleRequest: method=$method cbId=$cbId params=${params.toString().take(200)}")
        when (method) {
            "eth_requestAccounts", "eth_accounts" -> {
                val addr = bscAddress()
                jsCallback(cbId, JSONArray().put(addr).toString(), null)
                webView.evaluateJavascript("window.__xhbAccounts('$addr')", null)
            }
            "eth_chainId" -> jsCallback(cbId, "\"0x38\"", null)
            "net_version" -> jsCallback(cbId, "\"56\"", null)
            "eth_coinbase" -> jsCallback(cbId, "\"${bscAddress()}\"", null)
            "eth_sign", "personal_sign" -> confirmSign(method, params, cbId)
            "eth_sendTransaction" -> confirmSendTx(params, cbId)
            "eth_signTypedData", "eth_signTypedData_v3", "eth_signTypedData_v4" -> {
                jsCallback(cbId, null, "typed data signing not supported")
            }
            else -> proxyRpc(method, params, cbId)
        }
    }

    private fun bscAddress(): String {
        val mn = WalletStore.current()?.mnemonic ?: return "0x0000000000000000000000000000000000000000"
        val coin = MultiCoin.COINS.first { it.symbol == "BNB" }
        return MultiCoin.bscAddress(mn, coin)
    }

    private fun bscEcKey() = Bscs.derivePrivateKey(WalletStore.current()?.mnemonic ?: "", MultiCoin.COINS.first { it.symbol == "BNB" })

    /** 签名确认弹窗 */
    private fun confirmSign(method: String, params: JSONArray, cbId: Int) {
        val message = if (method == "personal_sign") params.optString(0) else params.optString(1)
        val decoded = try { String(hexToBytes(message.removePrefix("0x"))) } catch (_: Exception) { message }
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(tv(Nav.act, "签名请求", 18f, C.TXT, true).apply { gravity = Gravity.CENTER; setPadding(0, 16.dp(), 0, 8.dp()) })
        body.addView(tv(Nav.act, "网站请求签名以下消息：", 13f, C.SUB).apply { setPadding(16.dp(), 8.dp(), 16.dp(), 4.dp()) })
        val msgTv = tv(Nav.act, decoded.take(500), 12f, C.TXT).apply {
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
            setBackgroundColor(C.CARD2)
        }
        val mlp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 4.dp(), 16.dp(), 8.dp()) }
        msgTv.layoutParams = mlp; body.addView(msgTv)
        body.addView(tv(Nav.act, "⚠️ 签名可能授权网站操作你的资产", 12f, C.RED).apply { setPadding(16.dp(), 4.dp(), 16.dp(), 12.dp()) })
        body.addView(primaryBtn(Nav.act, "确认签名") {
            Nav.closeSheet()
            val sig = signMessage(message)
            jsCallback(cbId, "\"$sig\"", null)
        })
        body.addView(ghostBtn(Nav.act, "拒绝") { Nav.closeSheet(); jsCallback(cbId, null, "User rejected") })
        Nav.sheet("签名确认", body)
    }

    private fun signMessage(hexMsg: String): String {
        val msg = hexToBytes(hexMsg.removePrefix("0x"))
        val prefix = "\u0019Ethereum Signed Message:\n${msg.size}".toByteArray()
        val hash = keccak256(prefix + msg)
        val ecKey = bscEcKey()
        val sig = ecKey.sign(org.bitcoinj.core.Sha256Hash.wrap(hash))
        val r = toBytes32(sig.r); val s = toBytes32(sig.s)
        val myPub = ecKey.pubKeyPoint
        var recid = 0
        for (i in 0..3) {
            try {
                val rec = org.bitcoinj.core.ECKey.recoverFromSignature(i, sig, org.bitcoinj.core.Sha256Hash.wrap(hash), false)
                if (rec != null && rec.pubKeyPoint.equals(myPub)) { recid = i; break }
            } catch (_: Exception) {}
        }
        val v = (recid + 27).toByte()
        return "0x" + org.bouncycastle.util.encoders.Hex.toHexString(r + s + byteArrayOf(v))
    }

    /** 发送交易确认弹窗 */
    private fun confirmSendTx(params: JSONArray, cbId: Int) {
        val tx = params.getJSONObject(0)
        val to = tx.optString("to")
        val valueHex = tx.optString("value", "0x0")
        val valueWei = BigInteger(valueHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val valueBnb = valueWei.toDouble() / 1e18
        val gasHex = tx.optString("gas", "0x5208")
        val gasPriceHex = tx.optString("gasPrice", "0x0")
        val data = tx.optString("data", "0x")

        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(tv(Nav.act, "交易确认", 18f, C.TXT, true).apply { gravity = Gravity.CENTER; setPadding(0, 16.dp(), 0, 8.dp()) })
        body.addView(infoRow("发送到", to))
        body.addView(infoRow("金额", "%.6f BNB".format(valueBnb)))
        body.addView(infoRow("Gas Limit", BigInteger(gasHex.removePrefix("0x"), 16).toString()))
        if (gasPriceHex != "0x0") body.addView(infoRow("Gas Price", BigInteger(gasPriceHex.removePrefix("0x"), 16).divide(BigInteger.valueOf(1000000000)).toString() + " Gwei"))
        if (data != "0x" && data != "") body.addView(infoRow("数据", data.take(60) + "..."))
        body.addView(tv(Nav.act, "⚠️ 确认后将签名并广播交易", 12f, C.RED).apply { setPadding(16.dp(), 8.dp(), 16.dp(), 12.dp()) })
        body.addView(primaryBtn(Nav.act, "确认发送") {
            Nav.closeSheet()
            Nav.io {
                try {
                    val txHash = sendRawTransaction(tx)
                    Nav.ui { jsCallback(cbId, "\"$txHash\"", null) }
                } catch (e: Exception) {
                    Nav.ui { jsCallback(cbId, null, e.message ?: "send failed") }
                }
            }
        })
        body.addView(ghostBtn(Nav.act, "拒绝") { Nav.closeSheet(); jsCallback(cbId, null, "User rejected") })
        Nav.sheet("发送交易", body)
    }

    private fun infoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(Nav.act); row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(16.dp(), 6.dp(), 16.dp(), 6.dp())
        row.addView(tv(Nav.act, label, 12f, C.SUB).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 0.4f) })
        row.addView(tv(Nav.act, value, 12f, C.TXT).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f) })
        return row
    }

    /** 签名并广播交易（复用 Bscs 的 RLP+EIP-155） */
    private fun sendRawTransaction(tx: JSONObject): String {
        val ecKey = bscEcKey()
        val from = bscAddress()
        val to = tx.optString("to")
        val value = BigInteger(tx.optString("value", "0x0").removePrefix("0x").ifEmpty { "0" }, 16)
        val data = hexToBytes(tx.optString("data", "0x").removePrefix("0x"))
        val gasLimit = BigInteger(tx.optString("gas", "0x5208").removePrefix("0x"), 16)

        var node = ""
        var nonce: BigInteger? = null
        var gasPrice: BigInteger? = null
        for (n in CoinBalances.BSC_NODES) {
            val nonceRes = rpc(n, "eth_getTransactionCount", listOf(from, "pending")) ?: continue
            val gasRes = rpc(n, "eth_gasPrice", listOf()) ?: continue
            nonce = BigInteger(nonceRes.getString("result").removePrefix("0x"), 16)
            gasPrice = BigInteger(gasRes.getString("result").removePrefix("0x"), 16)
            node = n; break
        }
        if (nonce == null || gasPrice == null) throw Exception("节点不可达")

        val toBytes = org.bouncycastle.util.encoders.Hex.decode(to.removePrefix("0x"))
        val chainId = 56L

        val signingItems = listOf(
            rlpEncode(bi(nonce)), rlpEncode(bi(gasPrice)), rlpEncode(bi(gasLimit)),
            rlpEncode(toBytes), rlpEncode(bi(value)), rlpEncode(data),
            rlpEncode(bi(BigInteger.valueOf(chainId))), rlpEncode(ByteArray(0)), rlpEncode(ByteArray(0)),
        )
        val hash = keccak256(rlpEncodeList(signingItems))
        val sig = ecKey.sign(org.bitcoinj.core.Sha256Hash.wrap(hash))
        val r = toBytes32(sig.r); val s = toBytes32(sig.s)
        val myPub = ecKey.pubKeyPoint
        var recid = 0
        for (i in 0..3) {
            try {
                val rec = org.bitcoinj.core.ECKey.recoverFromSignature(i, sig, org.bitcoinj.core.Sha256Hash.wrap(hash), false)
                if (rec != null && rec.pubKeyPoint.equals(myPub)) { recid = i; break }
            } catch (_: Exception) {}
        }
        val v = chainId * 2 + 35 + recid
        val signedItems = listOf(
            rlpEncode(bi(nonce)), rlpEncode(bi(gasPrice)), rlpEncode(bi(gasLimit)),
            rlpEncode(toBytes), rlpEncode(bi(value)), rlpEncode(data),
            rlpEncode(bi(BigInteger.valueOf(v))), rlpEncode(r), rlpEncode(s),
        )
        val rawHex = "0x" + org.bouncycastle.util.encoders.Hex.toHexString(rlpEncodeList(signedItems))
        val res = rpc(node, "eth_sendRawTransaction", listOf(rawHex)) ?: throw Exception("广播失败")
        val err = res.optJSONObject("error")
        if (err != null) throw Exception(err.optString("message"))
        return res.getString("result")
    }

    /** 代理其他 RPC 方法到 BSC 节点 */
    private fun proxyRpc(method: String, params: JSONArray, cbId: Int) {
        Nav.io {
            try {
                val list = ArrayList<Any>()
                for (i in 0 until params.length()) list.add(params.get(i))
                for (node in CoinBalances.BSC_NODES) {
                    val res = rpc(node, method, list) ?: continue
                    val result = res.opt("result")
                    if (result != null) {
                        val json = when (result) {
                            is String -> "\"$result\""
                            else -> result.toString()
                        }
                        Nav.ui { jsCallback(cbId, json, null) }
                        return@io
                    }
                }
                Nav.ui { jsCallback(cbId, null, "RPC failed") }
            } catch (e: Exception) {
                Nav.ui { jsCallback(cbId, null, e.message) }
            }
        }
    }

    private fun rpc(node: String, method: String, params: List<Any>): JSONObject? {
        return try {
            val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method)
                .put("params", JSONArray(params)).toString()
            val c = (URL(node).openConnection() as HttpURLConnection)
            c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/json"); c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect(); JSONObject(text)
        } catch (e: Exception) { null }
    }

    private fun jsCallback(cbId: Int, result: String?, error: String?) {
        val r = result?.replace("\\", "\\\\")?.replace("'", "\\'") ?: "null"
        val e = error?.replace("\\", "\\\\")?.replace("'", "\\'")?.let { "'$it'" } ?: "null"
        webView.evaluateJavascript("window.__xhbCallback($cbId, $r, $e)", null)
    }

    // ===== 工具函数 =====
    private fun hexToBytes(hex: String): ByteArray = org.bouncycastle.util.encoders.Hex.decode(hex)
    private fun keccak256(data: ByteArray): ByteArray = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256().digest(data)
    private fun toBytes32(bi: BigInteger): ByteArray {
        val b = bi.toByteArray(); val out = ByteArray(32)
        if (b.size == 33) System.arraycopy(b, 1, out, 0, 32)
        else if (b.size <= 32) System.arraycopy(b, 0, out, 32 - b.size, b.size)
        else System.arraycopy(b, b.size - 32, out, 0, 32)
        return out
    }
    private fun bi(b: BigInteger): ByteArray {
        if (b == BigInteger.ZERO) return ByteArray(0)
        val arr = b.toByteArray()
        return if (arr.size > 1 && arr[0].toInt() == 0) arr.copyOfRange(1, arr.size) else arr
    }
    private fun rlpEncode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf(0x80.toByte())
        return when {
            data.size == 1 && (data[0].toInt() and 0xFF) < 0x80 -> data
            data.size <= 55 -> byteArrayOf((0x80 + data.size).toByte()) + data
            else -> { val len = BigInteger.valueOf(data.size.toLong()).toByteArray(); byteArrayOf((0xb7 + len.size).toByte()) + len + data }
        }
    }
    private fun rlpEncodeList(items: List<ByteArray>): ByteArray {
        val payload = items.fold(ByteArray(0)) { acc, item -> acc + item }
        return when {
            payload.size <= 55 -> byteArrayOf((0xc0 + payload.size).toByte()) + payload
            else -> { val len = BigInteger.valueOf(payload.size.toLong()).toByteArray(); byteArrayOf((0xf7 + len.size).toByte()) + len + payload }
        }
    }

    private fun tv(ctx: android.content.Context, text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx); t.text = text; t.textSize = size; t.setTextColor(color)
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD)
        return t
    }
    private fun primaryBtn(ctx: android.content.Context, text: String, onClick: () -> Unit): TextView {
        val b = TextView(ctx); b.text = text; b.textSize = 15f; b.setTextColor(C.TXT)
        b.gravity = Gravity.CENTER; b.setPadding(0, 14.dp(), 0, 14.dp())
        b.setBackgroundColor(C.BLUE)
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 8.dp(), 16.dp(), 4.dp()) }
        b.layoutParams = lp; b.setOnClickListener { onClick() }; return b
    }
    private fun ghostBtn(ctx: android.content.Context, text: String, onClick: () -> Unit): TextView {
        val b = TextView(ctx); b.text = text; b.textSize = 15f; b.setTextColor(C.SUB)
        b.gravity = Gravity.CENTER; b.setPadding(0, 14.dp(), 0, 14.dp())
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 4.dp(), 16.dp(), 12.dp()) }
        b.layoutParams = lp; b.setOnClickListener { onClick() }; return b
    }
    private fun Int.dp(): Int = (this * Nav.act.resources.displayMetrics.density).toInt()
}
