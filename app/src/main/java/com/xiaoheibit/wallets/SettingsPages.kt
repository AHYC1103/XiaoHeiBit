package com.xiaoheibit.wallets

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 更多页 / 设置页 / 网络切换 / 自定义节点 */
object SettingsPages {
    private lateinit var setWalletCnt: TextView
    private lateinit var setHeight: TextView
    private lateinit var setValid: TextView

    private fun alpha(color: Int, a: Int = 0x26): Int = (color and 0x00FFFFFF) or (a shl 24)

    private fun titleBar(title: String): LinearLayout {
        val bar = LinearLayout(Nav.act); bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(12.dp(), 38.dp(), 12.dp(), 8.dp())
        val l = View(Nav.act); l.layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
        val t = tv(Nav.act, title, 17f, C.TXT, true); t.gravity = Gravity.CENTER
        t.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val r = View(Nav.act); r.layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
        bar.addView(l); bar.addView(t); bar.addView(r)
        return bar
    }

    private fun rawIcon(ctx: android.content.Context, res: Int): ImageView {
        val iv = ImageView(ctx)
        iv.setImageResource(res)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        return iv
    }

    private fun gridCell(emoji: String, label: String, dim: Boolean, onClick: () -> Unit): LinearLayout {
        val cell = LinearLayout(Nav.act); cell.orientation = LinearLayout.VERTICAL; cell.gravity = Gravity.CENTER
        cell.background = solid(C.CARD, 16f); cell.setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
        if (dim) cell.alpha = 0.5f
        val lp = LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(0, 0, 6.dp(), 0); cell.layoutParams = lp
        val e = tv(Nav.act, emoji, 28f, C.TXT); e.setPadding(0, 0, 0, 8.dp()); cell.addView(e)
        cell.addView(tv(Nav.act, label, 14f, C.TXT))
        cell.setOnClickListener { onClick() }
        return cell
    }

    fun buildMore(): View {
        val sv = ScrollView(Nav.act); val box = LinearLayout(Nav.act); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(titleBar("更多"))
        val pad = LinearLayout(Nav.act); pad.orientation = LinearLayout.VERTICAL; pad.setPadding(16.dp(), 4.dp(), 16.dp(), 0)
        val r1 = LinearLayout(Nav.act); r1.layoutParams = LinearLayout.LayoutParams(-1, -2)
        r1.addView(gridCell("🌐", "DAPP", true) { Nav.toast("DAPP 开发中") })
        r1.addView(gridCell("🖼️", "NFT", false) { Nav.go("nft") })
        val r2 = LinearLayout(Nav.act); val r2lp = LinearLayout.LayoutParams(-1, -2); r2lp.topMargin = 12.dp(); r2.layoutParams = r2lp
        r2.addView(gridCell("🛠️", "制作NFT", false) { Nav.go("nftMint") })
        r2.addView(gridCell("🔄", "交换", true) { Nav.toast("交换 开发中") })
        pad.addView(r1); pad.addView(r2)
        box.addView(pad)
        return sv
    }

    fun buildSet(): View {
        val sv = ScrollView(Nav.act); val box = LinearLayout(Nav.act); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(titleBar("设置"))

        val g1 = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_wallet, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()), "钱包管理", "--", onClick = { WS.wallets() }).also { setWalletCnt = it.getChildAt(2) as TextView },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x22C55E.toInt()), 0xFF22C55E.toInt()), "链状态", "--", onClick = { WS.chain() }).also { setHeight = it.getChildAt(2) as TextView },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_mine, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()), "挖矿打包", "", onClick = { WS.mine() })
        ))
        box.addView(g1)

        val g2 = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_wc, alpha(0x06B6D4.toInt()), 0xFF06B6D4.toInt()), "WalletConnect", "扫码连接", onClick = { Nav.go("wc") }),
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_dapp, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()), "DApp 浏览器", "BSC", onClick = { Nav.go("dapp") })
        ))
        box.addView(g2)

        val validItem = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_shield, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
            "链完整性校验", "--", "自动校验，无需操作")
        setValid = validItem.getChildAt(2) as TextView
        val about = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_info, C.CARD2, 0xFF8B8B98.toInt()), "关于", "") { openAbout() }
        val g3 = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_prefs, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()), "偏好设置", "") { openPrefs() },
            validItem,
            about
        ))
        box.addView(g3)
        return sv
    }

    fun bindSet() {
        if (::setWalletCnt.isInitialized) setWalletCnt.text = "${State.wallets.size}个"
        if (::setHeight.isInitialized) setHeight.text = "#${State.height}"
        Nav.io { try { val v = Api.validate(); Nav.ui { if (::setValid.isInitialized) setValid.text = if (v.optBoolean("valid")) "✓ 通过" else "✗ 异常" } }
            catch (_: Exception) { } }
    }

    private fun openAbout() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        // 联系方式分组
        val label = tv(Nav.act, "联系方式", 13f, C.SUB)
        label.setPadding(20.dp(), 8.dp(), 16.dp(), 8.dp())
        body.addView(label)
        val contacts = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_wechat, alpha(0x22C55E.toInt()), 0xFF22C55E.toInt()), "微信", "HeYuCheng20111103") {
                try {
                    Nav.act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("weixin://contacts/profile/HeYuCheng20111103")))
                } catch (e: Exception) {
                    val cm = Nav.act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("微信号", "HeYuCheng20111103"))
                    Nav.toast("微信未安装，微信号已复制")
                }
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_xsocial, alpha(0x111111.toInt()), 0xFF111111.toInt()), "X", "@HeYuCheng1103") {
                Nav.act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://x.com/HeYuCheng1103")))
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_telegram, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()), "电报", "@XiaoHei1103") {
                Nav.act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/XiaoHei1103")))
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_github, alpha(0x6B7280.toInt()), 0xFF6B7280.toInt()), "GitHub", "AHYC1103") {
                Nav.act.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/AHYC1103")))
            }
        ))
        body.addView(contacts)
        // 版本号
        val verItem = LinearLayout(Nav.act); verItem.gravity = Gravity.CENTER_VERTICAL
        verItem.setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())
        verItem.addView(tv(Nav.act, "版本", 15f, C.TXT))
        val verRight = LinearLayout(Nav.act); verRight.orientation = LinearLayout.VERTICAL
        verRight.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val verName = tv(Nav.act, Nav.act.packageManager.getPackageInfo(Nav.act.packageName, 0).versionName ?: "", 14f, C.SUB); verName.gravity = Gravity.RIGHT
        verRight.addView(verName)
        verItem.addView(verRight)
        body.addView(verItem)
        Nav.sheet("关于", body)
    }

    private fun openPrefs() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()),
            "切换网络", netName()) { openNet() })
        Nav.sheet("偏好设置", body)
    }

    private fun openMore() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val item = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_swap, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()),
            "切换网络", netName()) { openNet() }
        body.addView(item)
        Nav.sheet("更多", body)
    }
    private fun netName(): String {
        val u = Api.baseUrl.lowercase()
        return when {
            u.contains("8234") -> "测试网"
            u.contains("18222") || u.contains("ofalias") -> "公开主链"
            else -> "主网"
        }
    }

    private fun openNet() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.addView(netRowUrl("公开主链", "公开主链", 0xFFEF4444.toInt(), "http://cn-hk-bgp-4.ofalias.net:18222"))
        body.addView(netRowUrl("主网", "端口 8222", 0xFF22C55E.toInt(), "http://127.0.0.1:8222"))
        body.addView(netRowUrl("测试网", "端口 8234", 0xFFF59E0B.toInt(), "http://127.0.0.1:8234"))
        val custom = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x3B82F6.toInt()), C.BLUE2),
            "自定义节点", Api.baseUrl.removePrefix("http://")) { openCustom() }
        body.addView(custom)
        Nav.closeSheet(); Nav.sheet("切换网络", body)
    }
    private fun netRowUrl(name: String, sub: String, dot: Int, url: String): LinearLayout {
        val item = LinearLayout(Nav.act); item.gravity = Gravity.CENTER_VERTICAL; item.setPadding(16.dp(), 13.dp(), 16.dp(), 13.dp())
        val dotTv = tv(Nav.act, "●", 16f, dot); dotTv.layoutParams = LinearLayout.LayoutParams(36.dp(), -2); dotTv.gravity = Gravity.CENTER
        item.addView(dotTv)
        val mid = LinearLayout(Nav.act); mid.orientation = LinearLayout.VERTICAL
        mid.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        mid.addView(tv(Nav.act, name, 15f, C.TXT))
        mid.addView(tv(Nav.act, sub, 12f, C.SUB))
        item.addView(mid)
        if (Api.baseUrl == url) item.addView(tv(Nav.act, "✓", 15f, C.BLUE))
        item.setOnClickListener {
            Api.saveUrl(Nav.act, url); Nav.closeSheet()
            MainApp.refreshAll(); Nav.toast("已切换到$name")
        }
        return item
    }
    private fun openCustom() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val f = field(Nav.act, "节点地址", "http://127.0.0.1:8222")
        f.input.setText(Api.baseUrl)
        body.addView(f.root)
        body.addView(primaryBtn(Nav.act, "保存") {
            var u = f.input.text.toString().trim()
            if (u.isBlank()) return@primaryBtn
            if (!u.startsWith("http")) u = "http://$u"
            Api.saveUrl(Nav.act, u); Nav.closeSheet(); MainApp.refreshAll(); Nav.toast("已保存节点")
        })
        Nav.sheet("自定义节点", body)
    }
}
