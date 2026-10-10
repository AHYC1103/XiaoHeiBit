package com.xiaoheicoin.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** 钱包 / 交易相关底部弹窗（对应 webui maskWallets/maskImport/maskBackup/maskExport/maskSend/maskRecv/maskMine/maskChain/maskConfirm/maskRename） */
object WS {
    val ICONS = listOf("💰","🪙","💎","🔑","📱","🏦","🐶","🚀","🌟","🔥","🎮","📊")

    /** emoji → 渐变背景色（每个头像独特配色） */
    private val ICON_COLORS = mapOf(
        "💰" to intArrayOf(0xFFFFB300.toInt(), 0xFFFF8F00.toInt()),
        "🪙" to intArrayOf(0xFFCD7F32.toInt(), 0xFFB87333.toInt()),
        "💎" to intArrayOf(0xFF00BCD4.toInt(), 0xFF0097A7.toInt()),
        "🔑" to intArrayOf(0xFF9C27B0.toInt(), 0xFF7B1FA2.toInt()),
        "📱" to intArrayOf(0xFF607D8B.toInt(), 0xFF455A64.toInt()),
        "🏦" to intArrayOf(0xFF3F51B5.toInt(), 0xFF303F9F.toInt()),
        "🐶" to intArrayOf(0xFFFF9800.toInt(), 0xFFF57C00.toInt()),
        "🚀" to intArrayOf(0xFFFF5722.toInt(), 0xFFE64A19.toInt()),
        "🌟" to intArrayOf(0xFFFFEB3B.toInt(), 0xFFFBC02D.toInt()),
        "🔥" to intArrayOf(0xFFF44336.toInt(), 0xFFD32F2F.toInt()),
        "🎮" to intArrayOf(0xFF673AB7.toInt(), 0xFF512DA8.toInt()),
        "📊" to intArrayOf(0xFF4CAF50.toInt(), 0xFF388E3C.toInt()),
    )

    /** 生成钱包头像渐变圆形背景 */
    fun avatarBg(icon: String, sizeDp: Int): GradientDrawable {
        val colors = ICON_COLORS[icon] ?: intArrayOf(0xFF2D5BE6.toInt(), 0xFF5B83F5.toInt())
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = (sizeDp / 2).dp().toFloat()
        }
    }

    fun copy(text: String) {
        val cm = Nav.act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("xhb", text))
        Nav.toast("已复制")
    }

    private fun avatar(emoji: String, size: Int = 44): TextView {
        val t = TextView(Nav.act)
        t.text = emoji
        t.textSize = if (size >= 40) 22f else 16f
        t.gravity = Gravity.CENTER
        t.background = solid(C.ORANGE, 100f)
        t.layoutParams = LinearLayout.LayoutParams(size.dp(), size.dp())
        return t
    }

    /** 通用确认框 */
    fun confirm(title: String, msg: String, onYes: () -> Unit) {
        val body = LinearLayout(Nav.act)
        body.orientation = LinearLayout.VERTICAL
        val t = tv(Nav.act, title, 17f, C.TXT, true)
        body.addView(t)
        val m = tv(Nav.act, msg, 14f, C.SUB)
        m.setPadding(0, 8.dp(), 0, 20.dp())
        body.addView(m)
        val row = LinearLayout(Nav.act)
        val no = ghostBtn(Nav.act, "取消") { Nav.closeSheet() }
        val yesLp = no.layoutParams as LinearLayout.LayoutParams
        val yes = ghostBtn(Nav.act, "确定") { Nav.closeSheet(); onYes() }
        yes.background = blueGrad(20f); yes.setTextColor(C.TXT)
        row.addView(no); row.addView(yes)
        body.addView(row)
        Nav.sheet("", body)
    }

    /** 钱包选择 */
    fun wallets() {
        val body = LinearLayout(Nav.act)
        body.orientation = LinearLayout.VERTICAL
        // 创建新钱包大按钮
        val createBtn = LinearLayout(Nav.act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 0, 16.dp(), 8.dp()) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(C.CARD)
            }
            setOnClickListener { createWallet() }
        }
        createBtn.addView(tv(Nav.act, "+", 24f, C.TXT).apply { setPadding(0, 0, 12.dp(), 0) })
        createBtn.addView(tv(Nav.act, "创建新的钱包", 16f, C.TXT, true))
        body.addView(createBtn)
        // 导入现有钱包大按钮
        val importBtn = LinearLayout(Nav.act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 18.dp())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(16.dp(), 0, 16.dp(), 16.dp()) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16.dp().toFloat()
                setColor(C.CARD)
            }
            setOnClickListener { openImport() }
        }
        importBtn.addView(tv(Nav.act, "↓", 24f, C.TXT).apply { setPadding(0, 0, 12.dp(), 0) })
        importBtn.addView(tv(Nav.act, "导入现有钱包", 16f, C.TXT, true))
        body.addView(importBtn)
        // 已置顶标签
        body.addView(tv(Nav.act, "已置顶", 13f, C.SUB, true).apply {
            setPadding(20.dp(), 4.dp(), 16.dp(), 8.dp())
        })
        // 直接从 WalletStore.list 构建（rename/delete 后立即最新），余额从 State 找
        val curAddr = WalletStore.current()?.address
        val balMap = State.wallets.associate { it.address to it.balance }
        WalletStore.list.forEachIndexed { idx, w ->
            val bal = balMap[w.address] ?: 0
            val isCurrent = w.address == curAddr
            val opt = LinearLayout(Nav.act)
            opt.gravity = Gravity.CENTER_VERTICAL
            opt.setPadding(20.dp(), 10.dp(), 16.dp(), 10.dp())
            val lp = LinearLayout.LayoutParams(-1, -2); opt.layoutParams = lp
            // 小图标（SVG）
            val ic = android.widget.ImageView(Nav.act)
            ic.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            ic.setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            ic.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt())
            ).apply { cornerRadius = 22.dp().toFloat() }
            ic.layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            val svgFile = if (w.icon.endsWith(".svg")) w.icon else "fixed_1.svg"
            SvgLoader.load(ic, SvgLoader.path(svgFile))
            opt.addView(ic)
            val mid = LinearLayout(Nav.act); mid.orientation = LinearLayout.VERTICAL
            val mlp = LinearLayout.LayoutParams(0, -2, 1f); mlp.marginStart = 10.dp(); mid.layoutParams = mlp
            mid.addView(tv(Nav.act, w.name, 15f, C.TXT, true))
            mid.addView(tv(Nav.act, "${w.address.take(8)}...${w.address.takeLast(4)} · $bal XHB", 12f, C.SUB))
            opt.addView(mid)
            if (isCurrent) {
                opt.addView(tv(Nav.act, "✓", 15f, C.BLUE, true))
            }
            // 设置图标（导出/删除）
            val setIc = android.widget.ImageView(Nav.act).apply {
                setImageResource(R.drawable.ic_settings)
                setColorFilter(C.SUB, android.graphics.PorterDuff.Mode.SRC_IN)
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                layoutParams = LinearLayout.LayoutParams(32.dp(), 32.dp())
                setOnClickListener { rename(idx, w.name, w.icon) }
            }
            opt.addView(setIc)
            opt.setOnClickListener {
                WalletStore.switchTo(Nav.act, idx)
                Nav.closeSheet()
                MainApp.refreshAll()
            }
            body.addView(opt)
            // 分隔线
            val div = View(Nav.act); div.setBackgroundColor(C.LINE)
            div.layoutParams = LinearLayout.LayoutParams(-1, 1).apply { marginStart = 20.dp() }
            body.addView(div)
        }
        Nav.sheet("钱包", body)
        // 后台异步刷新余额
        Nav.io { State.refresh() }
    }

    private fun smallAct(s: String, color: Int = C.SUB, onClick: () -> Unit = {}): TextView {
        val t = TextView(Nav.act); t.text = s; t.setTextColor(color); t.textSize = 16f
        t.setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
        t.setOnClickListener { onClick() }
        return t
    }

    fun createWallet() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        // 顶部标题
        val title = tv(Nav.act, "您将获得一组助记词", 16f, C.TXT, true)
        title.gravity = Gravity.CENTER; title.setPadding(16.dp(), 4.dp(), 16.dp(), 4.dp())
        body.addView(title)
        val sub = tv(Nav.act, "请务必妥善备份，这是恢复钱包的唯一凭证", 12f, C.SUB)
        sub.gravity = Gravity.CENTER; sub.setPadding(16.dp(), 0, 16.dp(), 16.dp())
        body.addView(sub)

        // 渐变圆形图标辅助
        fun gradIcon(startC: Int, endC: Int, res: Int): LinearLayout {
            val box = LinearLayout(Nav.act); box.gravity = Gravity.CENTER
            box.layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            box.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, intArrayOf(startC, endC)
            ).apply { cornerRadius = 22.dp().toFloat() }
            val iv = android.widget.ImageView(Nav.act)
            iv.setImageResource(res); iv.setColorFilter(0xFFFFFFFF.toInt())
            iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            iv.layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp())
            box.addView(iv)
            return box
        }

        // 三个提示卡片
        val tips = listOf(
            Triple(gradIcon(0xFF2D5BE6.toInt(), 0xFF5B83F5.toInt(), R.drawable.ic_shield),
                "妥善备份助记词", "请抄写助记词，保存在只有您能取用的安全位置。"),
            Triple(gradIcon(0xFFFF9314.toInt(), 0xFFFFB74D.toInt(), R.drawable.ic_info),
                "切勿泄露助记词", "任何人拿到助记词，都能控制钱包并转走资产。"),
            Triple(gradIcon(0xFFF84E4E.toInt(), 0xFFFF7676.toInt(), R.drawable.ic_wallet),
                "我们无法找回助记词", "请保管好备份，以免设备丢失后无法恢复钱包。")
        )
        tips.forEach { (iconV, t, d) ->
            val card = LinearLayout(Nav.act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 10.dp()) }
                background = solid(C.CARD, 16f)
            }
            card.addView(iconV)
            val textCol = LinearLayout(Nav.act).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            textCol.addView(tv(Nav.act, t, 14f, C.TXT, true))
            textCol.addView(tv(Nav.act, d, 12f, C.SUB))
            card.addView(textCol)
            body.addView(card)
        }

        // 继续按钮（带loading）
        val btn = primaryBtn(Nav.act, "继续") {}
        btn.setOnClickListener {
            btn.text = "创建中..."
            btn.isEnabled = false
            btn.alpha = 0.5f
            Nav.io {
                try {
                    val w = WalletStore.createWithMnemonic(Nav.act)
                    Nav.ui { Nav.closeSheet(); showBackup(w.mnemonic ?: "") }
                } catch (e: Exception) {
                    Nav.ui {
                        btn.text = "继续"; btn.isEnabled = true; btn.alpha = 1f
                        Nav.toast(e.message ?: "失败")
                    }
                }
            }
        }
        body.addView(btn)
        Nav.sheet("新建钱包", body)
    }

    /** 新建钱包后显示私钥备份页 */
    fun showPrivBackup(priv: String) {
        copyBuffer = priv
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL; body.gravity = Gravity.CENTER_HORIZONTAL
        val tip = tv(Nav.act, "请抄下这串私钥，这是你恢复钱包的唯一凭证", 13f, C.SUB)
        tip.gravity = Gravity.CENTER; tip.setPadding(12.dp(), 0, 12.dp(), 16.dp()); body.addView(tip)
        val key = TextView(Nav.act)
        key.text = priv; key.setTextColor(C.ORANGE); key.textSize = 13f
        key.typeface = Typeface.MONOSPACE; key.gravity = Gravity.CENTER
        key.background = solid(C.CARD, 10f); key.setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
        body.addView(key)
        val ts = System.currentTimeMillis() / 1000
        val qr = qrImage(Nav.act, "XiaoHeiCoin\$leading-In\$$priv\$$ts", 190)
        val qlp = qr.layoutParams; qlp.width = 200.dp(); qlp.height = 200.dp(); qr.layoutParams = qlp
        body.addView(qr)
        val warn = tv(Nav.act, "⚠️ 不要截图！不要转发！只写在纸上！丢了找不回！", 12f, C.ORANGE)
        warn.gravity = Gravity.CENTER; warn.setPadding(12.dp(), 8.dp(), 12.dp(), 14.dp()); body.addView(warn)
        body.addView(centerBtn("复制", true) { copy(priv) })
        body.addView(centerBtn("我已抄好", false) { Nav.closeSheet(); Nav.toast("钱包创建成功"); MainApp.refreshAll() })
        Nav.closeSheet(); Nav.sheet("备份私钥", body)
    }

    fun showBackup(words: String) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val tip = tv(Nav.act, "请按顺序抄下这12个单词，这是你恢复钱包的唯一凭证", 13f, C.SUB)
        tip.gravity = Gravity.CENTER; tip.setPadding(12.dp(), 0, 12.dp(), 16.dp())
        body.addView(tip)
        val grid = LinearLayout(Nav.act); grid.orientation = LinearLayout.VERTICAL
        val arr = words.split(" ")
        var i = 0
        while (i < arr.size) {
            val row = LinearLayout(Nav.act)
            for (j in 0..1) {
                if (i + j < arr.size) {
                    val cell = TextView(Nav.act)
                    cell.text = "${i + j + 1}. ${arr[i + j]}"
                    cell.typeface = Typeface.MONOSPACE; cell.textSize = 14f; cell.setTextColor(C.TXT)
                    cell.background = solid(C.CARD, 10f); cell.setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                    val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(if (j == 0) 0 else 5.dp(), 0, if (j == 0) 5.dp() else 0, 0)
                    cell.layoutParams = lp
                    row.addView(cell)
                }
            }
            val rlp = LinearLayout.LayoutParams(-1, -2); rlp.bottomMargin = 8.dp(); row.layoutParams = rlp
            grid.addView(row); i += 2
        }
        body.addView(grid)
        val warn = tv(Nav.act, "⚠️ 不要截图！不要转发！只写在纸上！丢了找不回！", 12f, C.ORANGE)
        warn.setPadding(12.dp(), 8.dp(), 12.dp(), 14.dp()); body.addView(warn)
        val done = primaryBtn(Nav.act, "我已抄好，完成备份") {
            Nav.closeSheet(); Nav.toast("钱包创建成功，请妥善保管助记词"); MainApp.refreshAll()
        }
        body.addView(done)
        Nav.closeSheet(); Nav.sheet("备份助记词", body)
    }

    fun openImport() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        var type = "private"
        var importChain = "TRX"  // 私钥导入时选链，默认TRX
        val tabPriv = TextView(Nav.act); val tabMne = TextView(Nav.act)
        fun styleTab(t: TextView, active: Boolean) {
            t.textSize = 14f; t.gravity = Gravity.CENTER; t.setPadding(10.dp(), 12.dp(), 10.dp(), 12.dp())
            if (active) { t.background = solid(C.BLUE, 12f); t.setTextColor(C.TXT) }
            else { t.background = solid(0x00000000, 12f); t.setTextColor(C.SUB) }
        }
        tabPriv.text = "私钥导入"; tabMne.text = "助记词导入"
        val tabBar = LinearLayout(Nav.act); tabBar.background = solid(C.CARD2, 12f); tabBar.setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        val tlp = LinearLayout.LayoutParams(-1, -2); tlp.bottomMargin = 16.dp(); tabBar.layoutParams = tlp
        tabPriv.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tabMne.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        tabBar.addView(tabPriv); tabBar.addView(tabMne)
        body.addView(tabBar)

        // 私钥导入时的链选择
        val chainLabel = tv(Nav.act, "选择链", 12f, C.SUB)
        chainLabel.setPadding(0, 0, 0, 6.dp())
        val chainRow = LinearLayout(Nav.act); chainRow.orientation = LinearLayout.HORIZONTAL
        chainRow.setPadding(0, 0, 0, 12.dp())
        val chains = listOf("TRX", "BNB", "XHB")
        val chainTabs = mutableListOf<TextView>()
        chains.forEach { c ->
            val t = TextView(Nav.act); t.text = c; t.textSize = 13f; t.gravity = Gravity.CENTER
            t.setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            t.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 6.dp() }
            t.setOnClickListener {
                importChain = c
                chainTabs.forEach { it.setBackgroundColor(0x00000000); it.setTextColor(C.SUB) }
                t.background = solid(C.BLUE, 12f); t.setTextColor(C.TXT)
            }
            chainRow.addView(t)
            chainTabs.add(t)
        }
        chainTabs[0].background = solid(C.BLUE, 12f); chainTabs[0].setTextColor(C.TXT)
        body.addView(chainLabel); body.addView(chainRow)

        val keyBox = makeArea("私钥 (64位hex)", "粘贴私钥...")
        val mneBox = makeArea("助记词 (12个英文单词，空格分隔)", "例如: apple banana cherry ...")
        body.addView(keyBox.root); body.addView(mneBox.root)
        fun sel(t: String) {
            type = t
            styleTab(tabPriv, t == "private"); styleTab(tabMne, t == "mnemonic")
            keyBox.root.visibility = if (t == "private") View.VISIBLE else View.GONE
            mneBox.root.visibility = if (t == "mnemonic") View.VISIBLE else View.GONE
            chainLabel.visibility = if (t == "private") View.VISIBLE else View.GONE
            chainRow.visibility = if (t == "private") View.VISIBLE else View.GONE
        }
        tabPriv.setOnClickListener { sel("private") }; tabMne.setOnClickListener { sel("mnemonic") }; sel("private")
        val go = primaryBtn(Nav.act, "导入") {
            Nav.io {
                try {
                    if (type == "private") WalletStore.importKey(Nav.act, keyBox.input.text.toString().trim(), importChain)
                    else WalletStore.importMnemonic(Nav.act, mneBox.input.text.toString().trim())
                    State.refresh()
                    Nav.ui { Nav.closeSheet(); Nav.toast("导入成功"); MainApp.refreshAll() }
                } catch (e: Exception) { Nav.ui { Nav.toast(e.message ?: "失败") } }
            }
        }
        body.addView(go)
        Nav.closeSheet(); Nav.sheet("导入钱包", body)
    }

    private fun makeArea(label: String, hint: String): Field {
        val root = LinearLayout(Nav.act); root.orientation = LinearLayout.VERTICAL
        val l = tv(Nav.act, label, 12f, C.SUB); l.setPadding(0, 4.dp(), 0, 6.dp()); root.addView(l)
        val e = EditText(Nav.act)
        e.hint = hint; e.setHintTextColor(C.SUB); e.setTextColor(C.TXT); e.textSize = 13f
        e.typeface = Typeface.MONOSPACE; e.minLines = 3
        e.background = solid(C.CARD2, 10f, 1, C.LINE); e.setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        e.layoutParams = LinearLayout.LayoutParams(-1, -2)
        root.addView(e)
        return Field(root, e)
    }

    fun exportChoose(index: Int) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val q = tv(Nav.act, "你要导出助记词还是密钥？", 14f, C.SUB)
        q.gravity = Gravity.CENTER; q.setPadding(12.dp(), 0, 12.dp(), 18.dp()); body.addView(q)
        val row = LinearLayout(Nav.act)
        val key = ghostBtn(Nav.act, "🔑 密钥") { showExport(index, "private_key") }
        key.background = solid(C.CARD2, 16f, 1, C.LINE)
        val mne = ghostBtn(Nav.act, "📝 助记词") { showExport(index, "mnemonic") }
        mne.background = blueGrad(20f)
        row.addView(key); row.addView(mne)
        body.addView(row)
        Nav.sheet("选择导出内容", body)
    }

    fun showExport(index: Int, type: String) {
        val w = WalletStore.list.getOrNull(index)
        if (w == null) { Nav.toast("钱包不存在"); return }
        Nav.ui {
            val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
            body.gravity = Gravity.CENTER_HORIZONTAL
            if (type == "mnemonic" && !w.mnemonic.isNullOrBlank()) {
                val words = w.mnemonic!!; copyBuffer = words
                val grid = LinearLayout(Nav.act); grid.orientation = LinearLayout.VERTICAL
                val arr = words.split(" ")
                var i = 0
                while (i < arr.size) {
                    val r = LinearLayout(Nav.act)
                    for (j in 0..1) if (i + j < arr.size) {
                        val cell = TextView(Nav.act)
                        cell.text = "${i + j + 1}. ${arr[i + j]}"
                        cell.typeface = Typeface.MONOSPACE; cell.textSize = 15f; cell.setTextColor(C.TXT)
                        cell.background = solid(C.CARD, 10f); cell.setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                        val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(if (j == 0) 0 else 5.dp(), 0, if (j == 0) 5.dp() else 0, 0)
                        cell.layoutParams = lp; r.addView(cell)
                    }
                    r.layoutParams = LinearLayout.LayoutParams(-1, -2); grid.addView(r); i += 2
                }
                body.addView(grid)
            } else {
                copyBuffer = w.privateKeyHex
                val key = tv(Nav.act, copyBuffer, 13f, C.ORANGE)
                key.typeface = Typeface.MONOSPACE; key.gravity = Gravity.CENTER
                key.setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                body.addView(key)
                val ts = System.currentTimeMillis() / 1000
                val qr = qrImage(Nav.act, "XiaoHeiCoin\$leading-In\$${copyBuffer}\$$ts", 190)
                val qlp = qr.layoutParams; qlp.width = 200.dp(); qlp.height = 200.dp(); qr.layoutParams = qlp
                body.addView(qr)
            }
            val warn = tv(Nav.act, "⚠️ 请勿泄露！不要截图！只写在纸上！丢了找不回！", 12f, C.ORANGE)
            warn.gravity = Gravity.CENTER; warn.setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp()); body.addView(warn)
            body.addView(centerBtn("复制", true) { copy(copyBuffer) })
            body.addView(centerBtn("其他方法导出", false) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(android.content.Intent.EXTRA_TEXT, copyBuffer)
                intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "XiaoHeiCoin Wallet Backup")
                Nav.act.startActivity(android.content.Intent.createChooser(intent, "导出钱包备份"))
            })
            body.addView(centerBtn("关闭", false) { Nav.closeSheet() })
            Nav.closeSheet()
            Nav.sheet(if (type == "mnemonic") "导出助记词" else "导出私钥", body)
        }
    }
    private var copyBuffer = ""
    private fun centerBtn(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val b = TextView(Nav.act); b.text = text; b.gravity = Gravity.CENTER
        b.textSize = 15f; b.setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
        b.background = if (primary) blueGrad(20f) else solid(C.CARD2, 16f, 1, C.LINE)
        b.setTextColor(if (primary) C.TXT else C.SUB)
        val lp = LinearLayout.LayoutParams(160.dp(), -2); lp.bottomMargin = 10.dp(); lp.gravity = Gravity.CENTER_HORIZONTAL; b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private var renameIcon = "💰"
    fun rename(index: Int, oldName: String, oldIcon: String) {
        renameIcon = oldIcon.ifBlank { "💰" }
        val w = WalletStore.list.getOrNull(index)
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(20.dp(), 0, 20.dp(), 20.dp())
        // 头像标签
        body.addView(tv(Nav.act, "头像", 12f, C.SUB).apply { setPadding(0, 8.dp(), 0, 8.dp()) })
        // 头像选择器：4个固定 + 更多（不滑动）
        val picker = LinearLayout(Nav.act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val FIXED = listOf("fixed_1.svg", "fixed_2.svg", "fixed_3.svg", "fixed_4.svg")
        fun avatarGrad(selected: Boolean) = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt())
        ).apply {
            cornerRadius = 22.dp().toFloat()
            if (selected) setStroke(3, 0xFFFFFFFF.toInt())
        }
        val iconCells = ArrayList<android.widget.ImageView>()
        fun refreshIconCells() {
            iconCells.forEachIndexed { i, c ->
                c.background = avatarGrad(FIXED[i] == renameIcon)
            }
        }
        FIXED.forEach { svg ->
            val cell = android.widget.ImageView(Nav.act).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { setMargins(0, 4.dp(), 8.dp(), 4.dp()) }
                setOnClickListener { renameIcon = svg; refreshIconCells() }
            }
            SvgLoader.load(cell, SvgLoader.path(svg))
            iconCells.add(cell)
            picker.addView(cell)
        }
        // 更多按钮
        val moreBtn = TextView(Nav.act).apply {
            text = "更多 ›"
            textSize = 12f
            setTextColor(C.SUB)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { setMargins(0, 4.dp(), 0, 4.dp()) }
            background = solid(C.CARD2, 22f)
            setOnClickListener { Nav.closeSheet(); iconPicker(index, oldName) }
        }
        picker.addView(moreBtn)
        refreshIconCells()
        body.addView(picker)
        // 名字输入
        val nameF = field(Nav.act, "名字", "")
        nameF.input.setText(oldName)
        body.addView(nameF.root)
        (nameF.root.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 12.dp()
        // 显示助记词/私钥入口
        if (w != null) {
            val exportRow = LinearLayout(Nav.act).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                background = solid(C.CARD, 16f)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12.dp(), 0, 0) }
            }
            exportRow.addView(tv(Nav.act, "🔑", 18f, C.TXT).apply { setPadding(0, 0, 12.dp(), 0) })
            exportRow.addView(tv(Nav.act, if (w.mnemonic.isNullOrBlank()) "显示私钥" else "显示助记词", 15f, C.TXT, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            exportRow.addView(tv(Nav.act, "›", 20f, C.SUB))
            exportRow.setOnClickListener {
                Nav.closeSheet()
                if (w.mnemonic.isNullOrBlank()) showExport(index, "private_key") else showExport(index, "mnemonic")
            }
            body.addView(exportRow)

            // 新建地址入口（仅助记词钱包）
            if (!w.mnemonic.isNullOrBlank()) {
                val addrRow = LinearLayout(Nav.act).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                    background = solid(C.CARD, 16f)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 8.dp(), 0, 0) }
                }
                addrRow.addView(tv(Nav.act, "➕", 18f, C.TXT).apply { setPadding(0, 0, 12.dp(), 0) })
                addrRow.addView(tv(Nav.act, "新建地址", 15f, C.TXT, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                addrRow.addView(tv(Nav.act, "›", 20f, C.SUB))
                addrRow.setOnClickListener {
                    val newIdx = WalletStore.addAddress(Nav.act, index)
                    if (newIdx >= 0) {
                        Nav.toast("已创建新地址 #${WalletStore.list[newIdx].addrIndex + 1}")
                        Nav.closeSheet()
                        wallets()
                        Nav.io { State.refresh() }
                        MainApp.refreshAll()
                    } else {
                        Nav.toast("创建失败")
                    }
                }
                body.addView(addrRow)
            }
        }
        // 完成按钮（居中）
        val save = primaryBtn(Nav.act, "完成") {
            val nm = nameF.input.text.toString().trim()
            if (nm.isBlank()) { Nav.toast("名字不能为空"); return@primaryBtn }
            WalletStore.rename(Nav.act, index, nm, renameIcon)
            Nav.closeSheet()
            wallets()
            Nav.io { State.refresh() }
            MainApp.refreshAll()
        }
        (save.layoutParams as LinearLayout.LayoutParams).apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
            topMargin = 20.dp()
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(save)
        // 删除钱包按钮（红色文字，透明背景，Gem风格）
        val del = tv(Nav.act, "删除钱包", 16f, 0xFFFF3B30.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 14.dp(), 0, 14.dp())
            setOnClickListener {
                Nav.closeSheet()
                val confirmBody = LinearLayout(Nav.act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                confirmBody.addView(tv(Nav.act, "⚠️ 删除后本地钱包数据将清除", 14f, C.RED, true).apply {
                    gravity = Gravity.CENTER; setPadding(16.dp(), 16.dp(), 16.dp(), 8.dp())
                })
                confirmBody.addView(tv(Nav.act, "请确认已备份助记词/私钥，删除后无法找回！", 12f, C.SUB).apply {
                    gravity = Gravity.CENTER; setPadding(16.dp(), 0, 16.dp(), 16.dp())
                })
                confirmBody.addView(centerBtn("确认删除", true) {
                    WalletStore.delete(Nav.act, index)
                    Nav.closeSheet()
                    wallets()
                    Nav.io { State.refresh() }
                    MainApp.refreshAll()
                }.apply { setTextColor(0xFFFF3B30.toInt()) })
                confirmBody.addView(centerBtn("取消", false) { Nav.closeSheet() })
                Nav.sheet("确认删除", confirmBody)
            }
        }
        del.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 4.dp()
            gravity = Gravity.CENTER_HORIZONTAL
        }
        body.addView(del)
        Nav.sheet("钱包详情", body)
    }

    /** 更多图标选择页：网格每行3个 + 分类筛选 */
    fun iconPicker(index: Int, oldName: String) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(16.dp(), 0, 16.dp(), 16.dp())

        // 网格容器（先声明，供tab点击事件引用）
        val gridContainer = LinearLayout(Nav.act).apply { orientation = LinearLayout.VERTICAL }

        // 分类筛选标签
        val cats = listOf(null to "全部",
            SvgLoader.Category.CARTOON to "卡通",
            SvgLoader.Category.CRYPTO to "币圈",
            SvgLoader.Category.LETTER to "字母",
            SvgLoader.Category.TECH to "科技")
        var curCat: SvgLoader.Category? = null
        val tabRow = LinearLayout(Nav.act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8.dp(), 0, 8.dp()) }
        val tabViews = ArrayList<TextView>()
        cats.forEach { (cat, label) ->
            val tab = tv(Nav.act, label, 12f, C.SUB, true).apply {
                gravity = Gravity.CENTER
                setPadding(10.dp(), 6.dp(), 10.dp(), 6.dp())
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 6.dp(), 0) }
            }
            tab.setOnClickListener { }
            tabViews.add(tab); tabRow.addView(tab)
        }
        // 默认选中"全部"
        tabViews[0].setTextColor(0xFFFFFFFF.toInt())
        tabViews[0].background = solid(C.BLUE, 16f)
        body.addView(tabRow)

        // 网格容器（可滚动）
        val scroll = android.widget.ScrollView(Nav.act).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        scroll.addView(gridContainer)
        body.addView(scroll)

        // 返回按钮
        body.addView(primaryBtn(Nav.act, "返回") { Nav.closeSheet(); rename(index, oldName, renameIcon) })

        // 分页状态
        var allIcons = emptyList<String>()
        var loaded = 0
        val PAGE = 30
        fun loadMore() {
            val end = minOf(loaded + PAGE, allIcons.size)
            val batch = allIcons.subList(loaded, end)
            loaded = end
            batch.chunked(3).forEach { rowIcons ->
                val row = LinearLayout(Nav.act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                rowIcons.forEach { svg ->
                    val cell = android.widget.ImageView(Nav.act).apply {
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        layoutParams = LinearLayout.LayoutParams(0, 70.dp(), 1f).apply { setMargins(6.dp(), 6.dp(), 6.dp(), 6.dp()) }
                        setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                        setOnClickListener {
                            renameIcon = svg
                            Nav.closeSheet()
                            rename(index, oldName, svg)
                        }
                    }
                    if (svg == renameIcon) {
                        cell.background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 14.dp().toFloat(); setStroke(3, 0xFFFFFFFF.toInt())
                        }
                    }
                    SvgLoader.load(cell, SvgLoader.path(svg))
                    row.addView(cell)
                }
                repeat(3 - rowIcons.size) {
                    row.addView(View(Nav.act).apply { layoutParams = LinearLayout.LayoutParams(0, 70.dp(), 1f) })
                }
                gridContainer.addView(row)
            }
            // 移除旧的加载更多按钮
            (gridContainer.getChildAt(gridContainer.childCount - 1) as? TextView)?.let {
                if (it.text == "加载更多") gridContainer.removeView(it)
            }
            if (loaded < allIcons.size) {
                val more = tv(Nav.act, "加载更多", 13f, C.CYAN, true).apply {
                    gravity = Gravity.CENTER; setPadding(0, 12.dp(), 0, 12.dp())
                    setOnClickListener { loadMore() }
                }
                gridContainer.addView(more)
            }
        }
        fun resetGrid(cat: SvgLoader.Category?) {
            gridContainer.removeAllViews()
            allIcons = SvgLoader.filterBy(Nav.act, cat)
            loaded = 0
            loadMore()
        }
        // tab点击改用resetGrid
        cats.forEachIndexed { idx, (cat, label) ->
            tabViews[idx].setOnClickListener {
                curCat = cat
                tabViews.forEach { it.setTextColor(C.SUB); it.background = null }
                tabViews[idx].setTextColor(0xFFFFFFFF.toInt())
                tabViews[idx].background = solid(C.BLUE, 16f)
                resetGrid(cat)
            }
        }
        resetGrid(null)
        Nav.sheet("选择头像", body)
    }

    fun send() { try {
        // 先选币种
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val curW = WalletStore.current()
        val impChain = curW?.importedChain
        val coins = mutableListOf(MultiCoin.COINS[0])
        val extra = when {
            curW?.mnemonic != null -> MultiCoin.COINS.filter { !it.isNative }
            impChain == "TRX" -> MultiCoin.COINS.filter { it.symbol in setOf("TRX", "USDT") }
            impChain == "BNB" -> MultiCoin.COINS.filter { it.symbol == "BNB" }
            else -> emptyList()
        }
        coins.addAll(extra)
        for (coin in coins) {
            val row = LinearLayout(Nav.act); row.gravity = Gravity.CENTER_VERTICAL
            row.background = solid(C.CARD, 20f); row.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            val rlp = LinearLayout.LayoutParams(-1, -2); rlp.setMargins(16.dp(), 0, 16.dp(), 8.dp()); row.layoutParams = rlp
            val iconRes = when {
                coin.isNative -> R.drawable.ic_xhb
                coin.symbol == "TRX" -> R.drawable.ic_tron
                coin.symbol == "BNB" -> R.drawable.ic_bnb
                coin.symbol == "USDT" -> R.drawable.ic_usdt
                else -> R.drawable.ic_xhb
            }
            val ic = android.widget.ImageView(Nav.act)
            ic.setImageResource(iconRes)
            ic.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            ic.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp()); row.addView(ic)
            val info = LinearLayout(Nav.act); info.orientation = LinearLayout.VERTICAL
            val ilp = LinearLayout.LayoutParams(0, -2, 1f); ilp.marginStart = 12.dp(); info.layoutParams = ilp
            info.addView(tv(Nav.act, coin.name, 15f, C.TXT, true))
            info.addView(tv(Nav.act, coin.symbol, 12f, C.SUB))
            row.addView(info)
            val arrow = tv(Nav.act, "›", 20f, C.SUB); row.addView(arrow)
            row.setOnClickListener { Nav.closeSheet(); if (coin.isNative) sendXHB() else sendCoin(coin) }
            body.addView(row)
        }
        Nav.sheet("发送 - 选择币种", body)
    } catch (e: Throwable) { Nav.toast("发送页出错: ${e.message}") }
    }

    /** 发送其他币种（框架，公链对接后续） */
    private fun sendCoin(coin: MultiCoin.Coin) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val warn = tv(Nav.act, "⚠️ 请确认网络为 ${coin.chain}，发送错误网络的资产将无法找回", 11f, C.RED)
        warn.setPadding(16.dp(), 8.dp(), 16.dp(), 4.dp()); body.addView(warn)
        val hint = if (coin.symbol == "BNB") "0x..." else "T..."
        val addrLabel = tv(Nav.act, "收款地址", 12f, C.SUB)
        addrLabel.setPadding(16.dp(), 6.dp(), 16.dp(), 6.dp()); body.addView(addrLabel)
        val addrRow = LinearLayout(Nav.act); addrRow.gravity = Gravity.CENTER_VERTICAL
        addrRow.setPadding(16.dp(), 0, 16.dp(), 6.dp())
        val addrInput = EditText(Nav.act)
        addrInput.hint = hint; addrInput.setHintTextColor(C.SUB); addrInput.setTextColor(C.TXT); addrInput.textSize = 15f
        addrInput.background = solid(C.CARD2, 12f, 1, C.LINE); addrInput.setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
        addrInput.layoutParams = LinearLayout.LayoutParams(0, -2, 1f); addrRow.addView(addrInput)
        val scanBtn = LinearLayout(Nav.act); scanBtn.gravity = Gravity.CENTER; scanBtn.background = solid(C.CARD2, 10f)
        scanBtn.setPadding(10.dp(), 12.dp(), 10.dp(), 12.dp())
        val slp = LinearLayout.LayoutParams(-2, -2); slp.marginStart = 8.dp(); scanBtn.layoutParams = slp
        scanBtn.addView(icon(Nav.act, R.drawable.ic_scan, 20, C.TXT))
        scanBtn.setOnClickListener { ScanUtil.open { code -> addrInput.setText(code) } }
        addrRow.addView(scanBtn)
        // 地址簿按钮
        val abBtn = LinearLayout(Nav.act); abBtn.gravity = Gravity.CENTER; abBtn.background = solid(C.CARD2, 10f)
        abBtn.setPadding(10.dp(), 12.dp(), 10.dp(), 12.dp())
        val abLp = LinearLayout.LayoutParams(-2, -2); abLp.marginStart = 8.dp(); abBtn.layoutParams = abLp
        abBtn.addView(icon(Nav.act, R.drawable.ic_qrcode, 20, C.TXT))
        abBtn.setOnClickListener {
            val list = AddressBook.list(Nav.act)
            if (list.isEmpty()) { Nav.toast("地址簿为空，去设置→地址管理添加"); return@setOnClickListener }
            val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
            body.setPadding(0, 8.dp(), 0, 8.dp())
            val items = list.map { addr ->
                setItem(Nav.act, setIc(Nav.act, R.drawable.ic_qrcode, 0x3338BDF8, 0xFF38BDF8.toInt()),
                    addr.label.ifBlank { "(未命名)" }, addr.address.take(10) + "..." + addr.address.takeLast(6)) {
                    addrInput.setText(addr.address)
                    Nav.closeSheet()
                }
            }
            body.addView(setGroup(Nav.act, items))
            Nav.sheet("选择地址", body)
        }
        addrRow.addView(abBtn); body.addView(addrRow)
        val amtF = field(Nav.act, "数量 (${coin.symbol})", "0", numeric = true); body.addView(amtF.root)
        val feeTip = tv(Nav.act, "手续费：由网络波动决定（￥${"%.2f".format(Rates.cny(coin.symbol) * 0.001)}）", 12f, C.SUB)
        feeTip.setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp()); body.addView(feeTip)
        body.addView(primaryBtn(Nav.act, "确认发送") {
            val to = addrInput.text.toString().trim()
            val amt = amtF.input.text.toString().trim()
            if (to.isBlank() || amt.isBlank()) { Nav.toast("请填写地址和数量"); return@primaryBtn }
            val amount = amt.toDoubleOrNull() ?: 0.0
            // 余额检查
            val gasFee = 0.00001 // 最小手续费
            val currentBal = State.coinBalances[coin.symbol] ?: 0.0
            if (amount + gasFee > currentBal) {
                Nav.toast("余额不足：当前 %.4f %s，需 %.4f %s（含手续费）".format(currentBal, coin.symbol, amount + gasFee, coin.symbol))
                return@primaryBtn
            }
            val wallet = WalletStore.current()
            val mn = wallet?.mnemonic
            val priv = wallet?.privateKeyHex ?: ""
            if (mn == null && priv.isEmpty()) { Nav.toast("无可用密钥"); return@primaryBtn }

            fun doSend() {
                Nav.toast("正在广播...")
                Nav.io {
                    val (ok, msg) = when (coin.symbol) {
                        "TRX" -> if (mn != null) Trons.send(mn, coin, to, amount) else Trons.sendPriv(priv, to, amount)
                        "USDT" -> if (mn != null) Trons.sendUSDT(mn, coin, to, amount) else Trons.sendUSDTPriv(priv, to, amount)
                        "BNB" -> if (mn != null) Bscs.send(mn, coin, to, amount) else Bscs.sendPriv(priv, to, amount)
                        else -> Pair(false, "${coin.name} 发送对接中")
                    }
                    if (ok) {
                        val extra = RemoteConfig.feeOf(coin.symbol)
                        FeeCollector.record(Nav.act, coin.symbol, extra)
                    }
                    Nav.ui {
                        Nav.toast(if (ok) "✅ $msg" else "❌ $msg")
                        if (ok) { Nav.closeSheet(); State.refresh() }
                    }
                }
            }

            if (PasswordLock.isEnabled(Nav.act)) {
                PasswordLock.requestVerify(Nav.act, "验证密码后发送") { doSend() }
            } else {
                doSend()
            }
        })
        body.addView(centerBtn("取消", false) { Nav.closeSheet() })
        Nav.sheet("发送 ${coin.name}", body)
    }

    /** 发送 XHB */
    private fun sendXHB() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val ctx = Nav.act
        val warn = tv(ctx, "⚠️ 请确认网络为 XHB 主网，发送错误网络的资产将无法找回", 11f, C.RED)
        warn.setPadding(16.dp(), 8.dp(), 16.dp(), 4.dp()); body.addView(warn)
        val addrLabel = tv(ctx, "收款地址 (16位hex)", 12f, C.SUB)
        addrLabel.setPadding(16.dp(), 6.dp(), 16.dp(), 6.dp())
        body.addView(addrLabel)
        val row = LinearLayout(ctx); row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(16.dp(), 0, 16.dp(), 6.dp())
        val addrInput = EditText(ctx)
        addrInput.hint = "如 a1b2c3d4e5f60718"; addrInput.setHintTextColor(C.SUB)
        addrInput.setTextColor(C.TXT); addrInput.textSize = 15f
        addrInput.background = solid(C.CARD2, 12f, 1, C.LINE)
        addrInput.setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
        addrInput.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        row.addView(addrInput)
        val scan = LinearLayout(ctx); scan.gravity = Gravity.CENTER; scan.background = solid(C.CARD2, 10f)
        scan.setPadding(10.dp(), 12.dp(), 10.dp(), 12.dp()); val slp = LinearLayout.LayoutParams(-2, -2); slp.marginStart = 8.dp(); scan.layoutParams = slp
        scan.addView(icon(ctx, R.drawable.ic_scan, 20, C.TXT))
        scan.setOnClickListener { ScanUtil.open { code -> addrInput.setText(extractAddr(code)) } }
        row.addView(scan)
        body.addView(row)
        val amtF = field(Nav.act, "数量 (XHB)", "0", numeric = true)
        body.addView(amtF.root)
        val memoF = field(Nav.act, "备注（可选，最多200字）", "")
        body.addView(memoF.root)
        body.addView(primaryBtn(Nav.act, "确认发送") {
            val to = addrInput.text.toString().trim().lowercase()
            val amt = amtF.input.text.toString().trim().toDoubleOrNull()
            val memo = memoF.input.text.toString().trim()
            if (to.isBlank() || amt == null) { Nav.toast("请填写地址和数量"); return@primaryBtn }
            Nav.io { try { WalletStore.send(Nav.act, to, amt, memo); State.refresh(); Nav.ui { Nav.closeSheet(); Nav.toast("已入池: $amt XHB"); MainApp.refreshAll() } }
                catch (e: Exception) { Nav.ui { Nav.toast(e.message ?: "失败") } } }
        })
        body.addView(ghostBtn(Nav.act, "取消") { Nav.closeSheet() })
        Nav.sheet("发送 XHB", body)
    }

    fun extractAddr(code: String): String {
        val p = code.split("$")
        return if (p.size >= 3 && p[0] == "XiaoHeiCoin" && p[1] == "CollectMoney") p[2] else code
    }

    fun recv() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val curW = WalletStore.current()
        val impChain = curW?.importedChain
        val coins = mutableListOf(MultiCoin.COINS[0])
        val extra = when {
            curW?.mnemonic != null -> MultiCoin.COINS.filter { !it.isNative }
            impChain == "TRX" -> MultiCoin.COINS.filter { it.symbol in setOf("TRX", "USDT") }
            impChain == "BNB" -> MultiCoin.COINS.filter { it.symbol == "BNB" }
            else -> emptyList()
        }
        coins.addAll(extra)
        for (coin in coins) {
            val row = LinearLayout(Nav.act); row.gravity = Gravity.CENTER_VERTICAL
            row.background = solid(C.CARD, 20f); row.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            val rlp = LinearLayout.LayoutParams(-1, -2); rlp.setMargins(16.dp(), 0, 16.dp(), 8.dp()); row.layoutParams = rlp
            val iconRes = when {
                coin.isNative -> R.drawable.ic_xhb
                coin.symbol == "TRX" -> R.drawable.ic_tron
                coin.symbol == "BNB" -> R.drawable.ic_bnb
                coin.symbol == "USDT" -> R.drawable.ic_usdt
                else -> R.drawable.ic_xhb
            }
            val ic = android.widget.ImageView(Nav.act)
            ic.setImageResource(iconRes)
            ic.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            ic.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp()); row.addView(ic)
            val info = LinearLayout(Nav.act); info.orientation = LinearLayout.VERTICAL
            val ilp = LinearLayout.LayoutParams(0, -2, 1f); ilp.marginStart = 12.dp(); info.layoutParams = ilp
            info.addView(tv(Nav.act, coin.name, 15f, C.TXT, true))
            info.addView(tv(Nav.act, coin.symbol, 12f, C.SUB))
            row.addView(info)
            val arrow = tv(Nav.act, "›", 20f, C.SUB); row.addView(arrow)
            row.setOnClickListener {
                Nav.closeSheet()
                if (coin.isNative) recvXHB() else recvCoin(coin)
            }
            body.addView(row)
        }
        Nav.sheet("选择币种", body)
    }

    /** 接收 XHB */
    private fun recvXHB() {
        val addr = State.current ?: "--"
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL; body.gravity = Gravity.CENTER_HORIZONTAL
        val ts = System.currentTimeMillis() / 1000
        val data = "XiaoHeiCoin\$CollectMoney\$$addr\$$ts"
        val qr = qrImage(Nav.act, data, 200)
        val qlp = qr.layoutParams as LinearLayout.LayoutParams; qlp.setMargins(0, 14.dp(), 0, 14.dp()); qr.layoutParams = qlp
        body.addView(qr)
        body.addView(tv(Nav.act, "扫描二维码或复制地址，发送 XHB 到此地址", 13f, C.SUB).apply { setPadding(0, 0, 0, 8.dp()) })
        body.addView(tv(Nav.act, "⚠️ 请确认网络为 XHB 主网，发送错误网络的资产将无法找回", 11f, C.RED).apply { setPadding(0, 0, 0, 8.dp()) })
        val box = TextView(Nav.act); box.text = addr; box.setTextColor(C.CYAN); box.textSize = 14f
        box.typeface = Typeface.MONOSPACE; box.gravity = Gravity.CENTER
        box.background = solid(C.CARD2, 12f); box.setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        val blp = LinearLayout.LayoutParams(-1, -2); blp.setMargins(16.dp(), 0, 16.dp(), 10.dp()); box.layoutParams = blp
        body.addView(box)
        body.addView(centerBtn("复制地址", true) { copy(addr) })
        body.addView(centerBtn("关闭", false) { Nav.closeSheet() })
        Nav.sheet("接收 XHB", body)
    }

    /** 接收其他币种 */
    fun recvCoin(coin: MultiCoin.Coin) {
        try {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL; body.gravity = Gravity.CENTER_HORIZONTAL
        val curW = WalletStore.current()
        val mn = curW?.mnemonic
        val priv = curW?.privateKeyHex ?: ""
        if (mn == null && priv.isEmpty()) { Nav.toast("无可用密钥"); return }
        val addr = when (coin.symbol) {
            "BNB" -> if (mn != null) MultiCoin.bscAddress(mn, coin) else MultiCoin.bscAddressFromPriv(priv)
            "TRX", "USDT" -> if (mn != null) MultiCoin.tronAddress(mn, coin) else MultiCoin.tronAddressFromPriv(priv)
            else -> mn ?: priv
        }
        val tip = tv(Nav.act, "扫描二维码或复制地址，发送 ${coin.symbol} 到此地址", 13f, C.SUB)
        tip.gravity = Gravity.CENTER; tip.setPadding(12.dp(), 8.dp(), 12.dp(), 4.dp()); body.addView(tip)
        val warn = tv(Nav.act, "⚠️ 请确认网络为 ${coin.chain}，发送错误网络的资产将无法找回", 11f, C.RED)
        warn.gravity = Gravity.CENTER; warn.setPadding(12.dp(), 0, 12.dp(), 12.dp()); body.addView(warn)
        val ts = System.currentTimeMillis() / 1000
        // 二维码白色背景卡片
        val qrCard = LinearLayout(Nav.act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 8.dp(), 0, 8.dp()) }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20.dp().toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
        }
        val qr = qrImage(Nav.act, addr, 190)
        val qlp = qr.layoutParams; qlp.width = 200.dp(); qlp.height = 200.dp(); qr.layoutParams = qlp
        qrCard.addView(qr)
        body.addView(qrCard)
        val addrTv = tv(Nav.act, addr, 12f, C.BLUE)
        addrTv.typeface = Typeface.MONOSPACE; addrTv.gravity = Gravity.CENTER
        addrTv.setPadding(12.dp(), 12.dp(), 12.dp(), 4.dp()); body.addView(addrTv)
        body.addView(centerBtn("复制地址", true) { copy(addr) })
        body.addView(centerBtn("关闭", false) { Nav.closeSheet() })
        Nav.sheet("接收 ${coin.name}", body)
        } catch (e: Exception) {
            Nav.toast("打开失败: ${e.message}")
        }
    }

    fun mine() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val nF = field(Nav.act, "挖矿次数 (1-100)", "1", numeric = true)
        nF.input.setText("1")
        body.addView(nF.root)
        val log = tv(Nav.act, "", 13f, C.SUB); log.setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp()); body.addView(log)
        body.addView(primaryBtn(Nav.act, "开始挖矿") {
            val n = nF.input.text.toString().trim().toIntOrNull() ?: 1
            log.text = "⛏ 挖矿中…(ARM设备每块几秒~几十秒)"
            Nav.io {
                try {
                    val cur = WalletStore.current() ?: throw RuntimeException("没有钱包")
                    val d = Api.post("/api/mine", org.json.JSONObject().put("count", n).put("address", cur.address))
                    State.refresh()
                    Nav.ui { log.text = "✅ 完成 ${d.optInt("mined")} 块\n新余额: ${d.optInt("balance")} XHbit"; MainApp.refreshAll() }
                } catch (e: Exception) { Nav.ui { log.text = "❌ ${e.message}" } }
            }
        })
        body.addView(ghostBtn(Nav.act, "关闭") { Nav.closeSheet() })
        Nav.sheet("挖矿打包", body)
    }

    fun chain() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val info = TextView(Nav.act); info.text = "加载中…"; info.textSize = 13f; info.setTextColor(C.TXT)
        info.background = solid(C.CARD2, 12f); info.setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        val ilp = LinearLayout.LayoutParams(-1, -2); ilp.bottomMargin = 10.dp(); info.layoutParams = ilp
        body.addView(info)
        body.addView(ghostBtn(Nav.act, "关闭") { Nav.closeSheet() })
        Nav.sheet("链状态", body)
        Nav.io { try { val s = Api.status()
            Nav.ui { info.text = "区块高度: #${s.optInt("height")}\n待打包: ${s.optInt("mempool")} 笔\n" +
                "挖矿难度: ${"0".repeat(s.optInt("difficulty"))}…\n出块奖励: ${s.optInt("miner_reward", 50)} XHbit\n总量: ${s.optInt("supply")}" }
        } catch (e: Exception) { Nav.ui { info.text = "加载失败: ${e.message}" } } }
    }
}
