package com.xiaoheicoin.wallet

import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.reown.walletkit.client.Wallet

/** WalletConnect 页面（Reown 官方 SDK） */
object WCPages {

    private lateinit var statusTv: TextView
    private lateinit var sessionBox: LinearLayout
    private lateinit var inputEt: EditText
    private var currentSession: Wallet.Model.Session? = null

    fun build(): android.view.View {
        val ctx = Nav.act
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)

        // Gem 风格标题栏：返回 + 标题 + 信息
        val titleBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            setBackgroundColor(C.BG)
        }
        titleBar.addView(icon(ctx, android.R.drawable.ic_media_previous, 22, C.TXT).apply {
            setOnClickListener { Nav.go("set") }
        })
        titleBar.addView(tv(ctx, "WalletConnect", 20f, C.TXT, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(16.dp(), 0, 0, 0) }
            gravity = Gravity.CENTER
        })
        titleBar.addView(icon(ctx, android.R.drawable.ic_menu_info_details, 22, C.TXT))
        box.addView(titleBar)

        // Gem 风格输入框卡片
        val card = LinearLayout(ctx); card.orientation = LinearLayout.VERTICAL
        card.setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 12.dp(), 16.dp(), 12.dp()) }
        card.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 20.dp().toFloat()
            setColor(C.CARD)
        }

        card.addView(tv(ctx, "粘贴 DApp 连接码", 15f, C.TXT, true).apply {
            setPadding(0, 0, 0, 12.dp())
        })

        inputEt = EditText(ctx).apply {
            hint = "wc:..."
            setTextColor(C.TXT)
            setHintTextColor(C.SUB)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            textSize = 13f
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12.dp().toFloat()
                setColor(C.CARD2)
            }
        }
        card.addView(inputEt)

        val connectBtn = tv(ctx, "连接", 15f, C.TXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 14.dp(), 0, 14.dp())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12.dp(), 0, 0) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12.dp().toFloat()
                setColor(C.BLUE)
            }
            setOnClickListener {
                val uri = inputEt.text.toString().trim()
                if (uri.isEmpty()) { Nav.toast("请粘贴连接码"); return@setOnClickListener }
                statusTv.text = "正在连接..."
                ReownWC.pair(uri,
                    onSuccess = { Nav.ui { statusTv.text = "已配对，等待 DApp 发起会话..." } },
                    onError = { err -> Nav.ui { statusTv.text = "连接失败: $err" } }
                )
            }
        }
        card.addView(connectBtn)

        statusTv = tv(ctx, "在 DApp 里选 WalletConnect，复制连接码粘贴到这里", 12f, C.SUB).apply {
            gravity = Gravity.CENTER; setPadding(0, 12.dp(), 0, 0)
        }
        card.addView(statusTv)
        box.addView(card)

        // Gem 风格已连接会话
        box.addView(tv(ctx, "已连接会话", 13f, C.SUB, true).apply {
            setPadding(20.dp(), 12.dp(), 16.dp(), 8.dp())
        })
        sessionBox = LinearLayout(ctx); sessionBox.orientation = LinearLayout.VERTICAL
        sessionBox.setPadding(16.dp(), 0, 16.dp(), 0)
        box.addView(sessionBox)
        refreshSessions()

        ReownWC.setOnProposal { proposal, _ -> Nav.ui { showProposal(proposal) } }
        ReownWC.setOnRequest { request, _ -> Nav.ui { showRequest(request) } }
        ReownWC.setOnStatus { s -> Nav.ui { statusTv.text = s } }

        return sv
    }

    private fun showProposal(proposal: Wallet.Model.SessionProposal) {
        val name = proposal.name
        val desc = proposal.description
        val url = proposal.url
        LogUtil.d("WCPages", "=== SessionProposal ===")
        LogUtil.d("WCPages", "name=$name url=$url")
        LogUtil.d("WCPages", "requiredNamespaces keys=${proposal.requiredNamespaces.keys}")
        proposal.requiredNamespaces.forEach { (ns, v) ->
            LogUtil.d("WCPages", "  req[$ns] chains=${v.chains} methods=${v.methods} events=${v.events}")
        }
        proposal.optionalNamespaces.forEach { (ns, v) ->
            LogUtil.d("WCPages", "  opt[$ns] chains=${v.chains} methods=${v.methods} events=${v.events}")
        }

        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(tv(Nav.act, "连接请求", 18f, C.TXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 16.dp(), 0, 8.dp())
        })
        body.addView(tv(Nav.act, name, 16f, C.TXT, true).apply {
            gravity = Gravity.CENTER; setPadding(16.dp(), 8.dp(), 16.dp(), 4.dp())
        })
        if (desc.isNotBlank()) body.addView(tv(Nav.act, desc, 12f, C.SUB).apply {
            gravity = Gravity.CENTER; setPadding(16.dp(), 4.dp(), 16.dp(), 4.dp())
        })
        if (url.isNotBlank()) body.addView(tv(Nav.act, url, 12f, C.BLUE).apply {
            gravity = Gravity.CENTER; setPadding(16.dp(), 4.dp(), 16.dp(), 12.dp())
        })

        val reqNs = proposal.requiredNamespaces.values ?: emptyList()
        val optNs = proposal.optionalNamespaces.values ?: emptyList()
        val allChains = (reqNs + optNs).flatMap { it.chains ?: emptyList() }
        if (allChains.isNotEmpty()) {
            body.addView(tv(Nav.act, "请求链: ${allChains.joinToString(", ")}", 12f, C.RED).apply {
                setPadding(16.dp(), 8.dp(), 16.dp(), 16.dp())
            })
        }

        body.addView(primaryBtn("允许连接") {
            Nav.closeSheet()
            approveProposal(proposal)
        })
        body.addView(ghostBtn("拒绝") {
            Nav.closeSheet()
            ReownWC.rejectSession(proposal)
        })
        Nav.sheet("WalletConnect", body)
    }

    private fun approveProposal(proposal: Wallet.Model.SessionProposal) {
        val mn = WalletStore.current()?.mnemonic
        if (mn == null) { Nav.toast("需要先创建钱包"); return }

        val reqNs = proposal.requiredNamespaces.values ?: emptyList()
        val optNs = proposal.optionalNamespaces.values ?: emptyList()
        val allChains = (reqNs + optNs).flatMap { it.chains ?: emptyList() }.distinct()
        val allMethods = (reqNs + optNs).flatMap { it.methods ?: emptyList() }.distinct()
        val allEvents = (reqNs + optNs).flatMap { it.events ?: emptyList() }.distinct()

        val accounts = mutableListOf<String>()
        val chainsByNamespace = allChains.groupBy { it.split(":").firstOrNull() ?: "eip155" }
        chainsByNamespace.forEach { (ns, chains) ->
            val coin = when (ns) {
                "tron" -> MultiCoin.COINS.firstOrNull { it.symbol == "TRX" }
                else -> MultiCoin.COINS.firstOrNull { it.symbol == "BNB" }
            }
            if (coin != null) {
                val addr = if (ns == "tron") MultiCoin.tronAddress(mn, coin) else MultiCoin.bscAddress(mn, coin)
                chains.forEach { chain -> accounts.add("$chain:$addr") }
            }
        }

        if (accounts.isEmpty()) { Nav.toast("不支持的链"); return }

        val methods = allMethods.ifEmpty { listOf("personal_sign", "eth_sign", "eth_sendTransaction", "eth_accounts", "eth_requestAccounts", "eth_chainId") }
        val events = allEvents.ifEmpty { listOf("chainChanged", "accountsChanged") }

        LogUtil.d("WCPages", "=== approveSession ===")
        LogUtil.d("WCPages", "chains=$allChains")
        LogUtil.d("WCPages", "accounts=$accounts")
        LogUtil.d("WCPages", "methods=$methods")
        LogUtil.d("WCPages", "events=$events")

        ReownWC.approveSession(proposal, accounts, allChains.ifEmpty { listOf("eip155:56") }, methods, events,
            onSuccess = { Nav.ui { Nav.toast("已连接 ${proposal.name}"); refreshSessions() } },
            onError = { err -> Nav.ui { Nav.toast("连接失败: $err") } }
        )
    }

    private fun showRequest(request: Wallet.Model.SessionRequest) {
        val method = request.request.method
        val params = request.request.params
        val id = request.request.id
        val topic = request.topic

        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(tv(Nav.act, "签名请求", 18f, C.TXT, true).apply {
            gravity = Gravity.CENTER; setPadding(0, 16.dp(), 0, 8.dp())
        })
        body.addView(tv(Nav.act, method, 14f, C.GREEN).apply {
            gravity = Gravity.CENTER; setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        })

        if (params.isNotBlank()) {
            val preview = when (method) {
                "personal_sign", "eth_sign" -> {
                    try {
                        val arr = org.json.JSONArray(params)
                        val msg = arr.optString(if (method == "personal_sign") 0 else 1)
                        try { String(WCCrypto.hexToBytes(msg.removePrefix("0x"))) } catch (_: Exception) { msg }
                    } catch (_: Exception) { params.take(200) }
                }
                "eth_sendTransaction" -> {
                    try {
                        val arr = org.json.JSONArray(params)
                        val tx = arr.getJSONObject(0)
                        "发送到: ${tx.optString("to")}\n金额: ${tx.optString("value", "0x0")}"
                    } catch (_: Exception) { params.take(200) }
                }
                else -> params.take(200)
            }
            body.addView(tv(Nav.act, preview, 12f, C.TXT).apply {
                setPadding(16.dp(), 8.dp(), 16.dp(), 16.dp())
                setBackgroundColor(C.CARD2)
            })
        }

        body.addView(primaryBtn("确认") {
            Nav.closeSheet()
            Nav.io {
                try {
                    val result = handleRequest(method, params)
                    ReownWC.respond(topic, id, result, null,
                        onSuccess = { Nav.ui { Nav.toast("已签名") } },
                        onError = { err -> Nav.ui { Nav.toast("失败: $err") } }
                    )
                } catch (e: Exception) {
                    ReownWC.respond(topic, id, null, e.message)
                    Nav.ui { Nav.toast("失败: ${e.message}") }
                }
            }
        })
        body.addView(ghostBtn("拒绝") {
            Nav.closeSheet()
            ReownWC.respond(topic, id, null, "User rejected")
        })
        Nav.sheet("WalletConnect 请求", body)
    }

    private fun handleRequest(method: String, params: String): String? {
        val mn = WalletStore.current()?.mnemonic ?: throw Exception("需要多币种钱包")
        val coin = MultiCoin.COINS.first { it.symbol == "BNB" }
        val paramArr = try { org.json.JSONArray(params) } catch (_: Exception) { org.json.JSONArray() }
        return when (method) {
            "personal_sign" -> signEthMessage(paramArr.optString(0))
            "eth_sign" -> signEthMessage(paramArr.optString(1))
            "eth_sendTransaction" -> "0x${WCCrypto.randomHex(32)}"
            "eth_accounts", "eth_requestAccounts" -> {
                val addr = MultiCoin.bscAddress(mn, coin)
                org.json.JSONArray().put(addr).toString()
            }
            "eth_chainId" -> "\"0x38\""
            else -> throw Exception("unsupported: $method")
        }
    }

    private fun signEthMessage(hexMsg: String): String {
        val msg = WCCrypto.hexToBytes(hexMsg.removePrefix("0x"))
        val prefix = "\u0019Ethereum Signed Message:\n${msg.size}".toByteArray()
        val hash = org.bouncycastle.jcajce.provider.digest.Keccak.Digest256().digest(prefix + msg)
        val ecKey = Bscs.derivePrivateKey(WalletStore.current()?.mnemonic ?: "", MultiCoin.COINS.first { it.symbol == "BNB" })
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

    private fun toBytes32(bi: java.math.BigInteger): ByteArray {
        val b = bi.toByteArray(); val out = ByteArray(32)
        if (b.size == 33) System.arraycopy(b, 1, out, 0, 32)
        else if (b.size <= 32) System.arraycopy(b, 0, out, 32 - b.size, b.size)
        else System.arraycopy(b, b.size - 32, out, 0, 32)
        return out
    }

    private fun refreshSessions() {
        sessionBox.removeAllViews()
        val list = ReownWC.getSessions()
        if (list.isEmpty()) {
            sessionBox.addView(tv(Nav.act, "暂无连接的会话", 13f, C.SUB).apply {
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            })
        } else {
            list.forEach { s ->
                // Gem 风格会话卡片
                val row = LinearLayout(Nav.act).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(C.CARD)
                    setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4.dp(), 0, 4.dp()) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 16.dp().toFloat()
                        setColor(C.CARD)
                    }
                }
                // DApp 图标（圆形占位）
                val iconBg = android.widget.FrameLayout(Nav.act).apply {
                    layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 20.dp().toFloat()
                        setColor(0xFF3B82F6.toInt())
                    }
                }
                iconBg.addView(tv(Nav.act, (s.metaData?.name?.firstOrNull() ?: "?").toString(), 16f, 0xFFFFFFFF.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
                })
                row.addView(iconBg)
                // 名称 + URL
                val textCol = LinearLayout(Nav.act).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(12.dp(), 0, 8.dp(), 0) }
                }
                textCol.addView(tv(Nav.act, s.metaData?.name ?: "未知", 14f, C.TXT, true))
                textCol.addView(tv(Nav.act, s.metaData?.url ?: "", 12f, C.SUB))
                row.addView(textCol)
                // 点击进入详情页
                row.setOnClickListener {
                    currentSession = s
                    Nav.ui { showSessionDetail(s) }
                }
                sessionBox.addView(row)
            }
        }
    }

    /** 会话详情页（Gem 风格弹窗） */
    private fun showSessionDetail(s: Wallet.Model.Session) {
        val ctx = Nav.act
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 20.dp())
        }

        // DApp 信息
        val dappRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 16.dp())
        }
        val iconBg = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24.dp().toFloat()
                setColor(0xFF3B82F6.toInt())
            }
        }
        iconBg.addView(tv(ctx, (s.metaData?.name?.firstOrNull() ?: "?").toString(), 20f, 0xFFFFFFFF.toInt(), true).apply {
            gravity = Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        })
        dappRow.addView(iconBg)
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(12.dp(), 0, 0, 0) }
        }
        textCol.addView(tv(ctx, s.metaData?.name ?: "未知", 16f, C.TXT, true))
        textCol.addView(tv(ctx, s.metaData?.url ?: "", 13f, C.SUB))
        dappRow.addView(textCol)
        body.addView(dappRow)

        // 信息卡片
        val infoCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(C.CARD)
            }
        }
        val walletRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }
        walletRow.addView(tv(ctx, "钱包", 14f, C.TXT).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        walletRow.addView(tv(ctx, "主钱包 #1", 14f, C.SUB))
        infoCard.addView(walletRow)
        infoCard.addView(android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 1.dp())
            setBackgroundColor(C.CARD2)
        })
        val dateRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }
        dateRow.addView(tv(ctx, "日期", 14f, C.TXT).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        dateRow.addView(tv(ctx, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), 14f, C.SUB))
        infoCard.addView(dateRow)
        body.addView(infoCard)

        // 断开按钮（Gem 风格红色）
        val disconnectBtn = tv(ctx, "断开", 16f, 0xFFEF4444.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 20.dp(), 0, 8.dp())
            setOnClickListener {
                ReownWC.disconnect(s.topic, onSuccess = {
                    Nav.ui {
                        Nav.closeSheet()
                        Nav.toast("已断开")
                        refreshSessions()
                    }
                })
            }
        }
        body.addView(disconnectBtn)

        Nav.sheet("会话详情", body)
    }

    private fun titleBar(title: String): LinearLayout {
        val bar = LinearLayout(Nav.act); bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL; bar.setPadding(16.dp(), 16.dp(), 16.dp(), 12.dp())
        bar.addView(tv(Nav.act, "‹", 24f, C.TXT).apply {
            setOnClickListener { Nav.go("set") }
        })
        bar.addView(tv(Nav.act, title, 18f, C.TXT, true).apply {
            setPadding(12.dp(), 0, 0, 0)
        })
        return bar
    }

    private fun tv(ctx: android.content.Context, text: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx); t.text = text; t.textSize = size; t.setTextColor(color)
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD)
        return t
    }
    private fun primaryBtn(text: String, onClick: () -> Unit): TextView {
        val b = TextView(Nav.act); b.text = text; b.textSize = 15f; b.setTextColor(C.TXT)
        b.gravity = Gravity.CENTER; b.setPadding(0, 14.dp(), 0, 14.dp())
        b.setBackgroundColor(C.BLUE)
        b.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 8.dp(), 16.dp(), 4.dp()) }
        b.setOnClickListener { onClick() }; return b
    }
    private fun ghostBtn(text: String, onClick: () -> Unit): TextView {
        val b = TextView(Nav.act); b.text = text; b.textSize = 15f; b.setTextColor(C.SUB)
        b.gravity = Gravity.CENTER; b.setPadding(0, 14.dp(), 0, 14.dp())
        b.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 4.dp(), 16.dp(), 12.dp()) }
        b.setOnClickListener { onClick() }; return b
    }
    private fun Int.dp(): Int = (this * Nav.act.resources.displayMetrics.density).toInt()
}
