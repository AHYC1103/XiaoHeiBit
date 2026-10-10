package com.xiaoheicoin.wallet

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
    private lateinit var setAddrCnt: TextView
    private lateinit var setHeight: TextView
    private lateinit var setValid: TextView
    private lateinit var setSecurity: TextView

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
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_qrcode, alpha(0x38BDF8.toInt()), 0xFF38BDF8.toInt()), "地址管理", "--", onClick = { openAddressBook() }).also { setAddrCnt = it.getChildAt(2) as TextView },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x22C55E.toInt()), 0xFF22C55E.toInt()), "链状态", "--", onClick = { WS.chain() }).also { setHeight = it.getChildAt(2) as TextView },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_mine, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()), "挖矿打包", "", onClick = { WS.mine() })
        ))
        box.addView(g1)

        val g2 = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_wc, alpha(0x06B6D4.toInt()), 0xFF06B6D4.toInt()), "WalletConnect", "扫码连接", onClick = { Nav.go("wc") }),
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_dapp, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()), "DApp 浏览器", "BSC", onClick = { Nav.go("dapp") })
        ))
        box.addView(g2)

        val securityItem = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_shield, alpha(0xDC2626.toInt()), 0xFFDC2626.toInt()), "安全", if (PasswordLock.isEnabled(Nav.act)) "已开启" else "未开启", onClick = { openSecurity() })
        setSecurity = securityItem.getChildAt(2) as TextView
        val gSecurity = setGroup(Nav.act, listOf(securityItem))
        box.addView(gSecurity)

        val validItem = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_shield, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
            "链完整性校验", "--", "自动校验，无需操作")
        setValid = validItem.getChildAt(2) as TextView
        val about = setItem(Nav.act, setIc(Nav.act, R.drawable.ic_info, C.CARD2, 0xFF8B8B98.toInt()), "关于", "") { openAbout() }
        val g3 = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_prefs, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()), "更多设置", "") { openMoreSettings() },
            validItem,
            about
        ))
        box.addView(g3)
        return sv
    }

    fun bindSet() {
        if (::setWalletCnt.isInitialized) setWalletCnt.text = "${State.wallets.size}个"
        if (::setAddrCnt.isInitialized) setAddrCnt.text = "${AddressBook.list(Nav.act).size}个"
        if (::setHeight.isInitialized) setHeight.text = "#${State.height}"
        if (::setSecurity.isInitialized) setSecurity.text = if (PasswordLock.isEnabled(Nav.act)) "已开启" else "未开启"
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

    private fun openMoreSettings() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val g = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()),
                "多节点", "节点管理") { openP2P() },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_swap, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
                "网络选择", netName()) { openNet() }
        ))
        body.addView(g)
        Nav.sheet("更多设置", body)
    }

    private fun openSecurity() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val enabled = PasswordLock.isEnabled(Nav.act)
        val g = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_shield, alpha(0xDC2626.toInt()), 0xFFDC2626.toInt()),
                "密码", if (enabled) "已设置" else "未设置") { openPassword() }
        ))
        body.addView(g)
        Nav.sheet("安全", body)
    }

    private fun openPassword() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val enabled = PasswordLock.isEnabled(Nav.act)
        val items = mutableListOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_prefs, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
                if (enabled) "修改密码" else "设置密码", "4位数字") {
                PasswordLock.requestSet(Nav.act)
            }
        )
        if (enabled) {
            items.add(setItem(Nav.act, setIc(Nav.act, R.drawable.ic_close, alpha(0xEF4444.toInt()), 0xFFEF4444.toInt()),
                "关闭密码", "需要验证当前密码") {
                PasswordLock.requestClear(Nav.act)
            })
        }
        body.addView(setGroup(Nav.act, items))
        Nav.sheet("密码", body)
    }

    // ========== 地址管理 ==========
    private fun openAddressBook() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())

        // 添加地址按钮
        body.addView(setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_qrcode, alpha(0x38BDF8.toInt()), 0xFF38BDF8.toInt()),
                "添加地址", "保存常用收款地址") { showAddressForm(null) }
        )))

        // 地址列表
        val list = AddressBook.list(Nav.act)
        if (list.isEmpty()) {
            val empty = tv(Nav.act, "暂无地址，点击上方添加", 14f, C.SUB)
            empty.gravity = Gravity.CENTER; empty.setPadding(0, 24.dp(), 0, 24.dp())
            body.addView(empty)
        } else {
            val items = list.map { addr ->
                setItem(Nav.act, setIc(Nav.act, R.drawable.ic_qrcode, alpha(0x38BDF8.toInt()), 0xFF38BDF8.toInt()),
                    addr.label.ifBlank { "(未命名)" }, addr.address.take(10) + "..." + addr.address.takeLast(6),
                    addr.chain) {
                    // 点击地址显示操作菜单：修改、置顶、删除
                    showAddressActions(addr)
                }
            }
            body.addView(setGroup(Nav.act, items))
        }
        Nav.sheet("地址管理", body)
    }

    private fun showAddressActions(addr: AddressBook.Addr) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val g = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_prefs, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
                "修改地址", addr.label) { Nav.closeSheet(); showAddressForm(addr) },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x22C55E.toInt()), 0xFF22C55E.toInt()),
                "置顶地址", "移到最前面") {
                AddressBook.moveToTop(Nav.act, addr.address)
                Nav.toast("已置顶")
                Nav.closeSheet()
                openAddressBook()
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_close, alpha(0xEF4444.toInt()), 0xFFEF4444.toInt()),
                "删除地址", addr.address.take(10) + "...") {
                AddressBook.remove(Nav.act, addr.address)
                Nav.toast("已删除")
                Nav.closeSheet()
                openAddressBook()
            }
        ))
        body.addView(g)
        Nav.sheet("操作", body)
    }

    private fun showAddressForm(existing: AddressBook.Addr?) {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())

        val labelF = field(Nav.act, "备注名称", existing?.label ?: "")
        val addrF = field(Nav.act, "收款地址", existing?.address ?: "")
        val chainF = field(Nav.act, "链类型", existing?.chain ?: "XHB")

        body.addView(labelF.root)
        body.addView(addrF.root)
        body.addView(chainF.root)

        body.addView(primaryBtn(Nav.act, if (existing == null) "添加" else "保存") {
            val label = labelF.input.text.toString().trim()
            val address = addrF.input.text.toString().trim()
            val chain = chainF.input.text.toString().trim()
            if (address.isBlank()) { Nav.toast("地址不能为空"); return@primaryBtn }
            if (existing == null) {
                AddressBook.add(Nav.act, label, address, chain)
                Nav.toast("已添加")
            } else {
                AddressBook.update(Nav.act, existing.address, label, address, chain)
                Nav.toast("已修改")
            }
            Nav.closeSheet()
            openAddressBook()
        })
        Nav.sheet(if (existing == null) "添加地址" else "修改地址", body)
    }

    private fun openP2P() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val g = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x3B82F6.toInt()), 0xFF3B82F6.toInt()),
                "多节点信息", "--") { p2pInfo() },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_scan, alpha(0x22C55E.toInt()), 0xFF22C55E.toInt()),
                "扫描多节点", "自动发现") { p2pScan() },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_qrcode, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
                "添加多节点", "手动添加") { p2pAdd() },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_close, alpha(0xEF4444.toInt()), 0xFFEF4444.toInt()),
                "删除多节点", "管理节点") { p2pRemove() }
        ))
        body.addView(g)
        Nav.sheet("多节点", body)
    }

    private fun p2pInfo() {
        Nav.io {
            try {
                val r = Api.get("/api/p2p/status")
                val peers = r.optJSONArray("peers")
                val count = r.optInt("peer_count", 0)
                val running = r.optBoolean("running", false)
                val sb = StringBuilder()
                sb.append("同步状态: ${if (running) "运行中" else "已停止"}\n")
                sb.append("节点数量: $count\n")
                for (i in 0 until (peers?.length() ?: 0)) {
                    sb.append("  ${i + 1}. ${peers!!.getString(i)}\n")
                }
                Nav.ui { Nav.sheet("多节点信息", body = LinearLayout(Nav.act).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(tv(Nav.act, sb.toString(), 14f, C.TXT).apply { setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp()) })
                })}
            } catch (e: Exception) {
                Nav.ui { Nav.toast("获取失败: ${e.message}") }
            }
        }
    }

    private fun p2pScan() {
        Nav.toast("正在扫描节点...")
        Nav.io {
            try {
                Api.post("/api/p2p/sync", org.json.JSONObject())
                Nav.ui { Nav.toast("扫描完成，节点已自动发现") }
            } catch (e: Exception) {
                Nav.ui { Nav.toast("扫描失败: ${e.message}") }
            }
        }
    }

    private fun p2pAdd() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        val f = field(Nav.act, "节点地址", "http://IP:端口")
        body.addView(f.root)
        body.addView(primaryBtn(Nav.act, "添加") {
            var url = f.input.text.toString().trim()
            if (url.isBlank()) { Nav.toast("请输入地址"); return@primaryBtn }
            // 去掉http://前缀
            if (url.startsWith("http://")) url = url.removePrefix("http://")
            if (url.startsWith("https://")) url = url.removePrefix("https://")
            // 没端口默认补8222
            if (!url.contains(":")) url = "$url:8222"
            // 字符校验
            if (!url.matches(Regex("^[a-zA-Z0-9.:/]+$"))) {
                Nav.toast("只能包含字母、数字、. : /"); return@primaryBtn
            }
            // 分离host和端口
            val parts = url.split(":")
            if (parts.size != 2) { Nav.toast("格式错误"); return@primaryBtn }
            val host = parts[0]
            val port = parts[1].toIntOrNull()
            if (port == null || port !in 1..65535) {
                Nav.toast("端口号无效 (1-65535)"); return@primaryBtn
            }
            // 如果是纯数字IP，校验格式（4段，每段0-255）
            if (host.matches(Regex("^[0-9.]+$"))) {
                val segs = host.split(".")
                if (segs.size != 4) { Nav.toast("IP格式错误，应为4段"); return@primaryBtn }
                for (s in segs) {
                    val n = s.toIntOrNull()
                    if (n == null || n !in 0..255) { Nav.toast("IP段无效 (0-255)"); return@primaryBtn }
                }
            }
            Nav.io {
                try {
                    Api.post("/api/p2p/peer/add", org.json.JSONObject().put("url", url))
                    Nav.ui { Nav.closeSheet(); Nav.toast("已添加节点") }
                } catch (e: Exception) {
                    Nav.ui { Nav.toast("添加失败: ${e.message}") }
                }
            }
        })
        Nav.sheet("添加多节点", body)
    }

    private fun p2pRemove() {
        Nav.io {
            try {
                val r = Api.get("/api/p2p/status")
                val peers = r.optJSONArray("peers")
                if (peers == null || peers.length() == 0) {
                    Nav.ui { Nav.toast("暂无节点") }
                    return@io
                }
                val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
                for (i in 0 until peers.length()) {
                    val url = peers.getString(i)
                    body.addView(setItem(Nav.act, setIc(Nav.act, R.drawable.ic_close, alpha(0xEF4444.toInt()), 0xFFEF4444.toInt()),
                        url, "点击删除") {
                        Nav.io {
                            try {
                                Api.post("/api/p2p/peer/remove", org.json.JSONObject().put("url", url))
                                Nav.ui { Nav.closeSheet(); Nav.toast("已删除") }
                            } catch (e: Exception) {
                                Nav.ui { Nav.toast("删除失败") }
                            }
                        }
                    })
                }
                Nav.ui { Nav.sheet("删除多节点", body) }
            } catch (e: Exception) {
                Nav.ui { Nav.toast("获取节点列表失败") }
            }
        }
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
            u.contains("18222") || u.contains("ofalias") -> "XiaoHeiChain 主网"
            u.contains("8222") -> "XiaoHeiChain 测试网"
            else -> "自定义"
        }
    }

    private fun openNet() {
        val body = LinearLayout(Nav.act); body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, 8.dp(), 0, 8.dp())
        val g = setGroup(Nav.act, listOf(
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0xEF4444.toInt()), 0xFFEF4444.toInt()),
                "XiaoHeiChain 主网", if (Api.baseUrl.contains("18222") || Api.baseUrl.contains("ofalias")) "✓ 当前" else "") {
                Api.saveUrl(Nav.act, "http://cn-hk-bgp-4.ofalias.net:18222")
                Nav.closeSheet(); MainApp.refreshAll(); Nav.toast("已切换到 XiaoHeiChain 主网")
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0xF59E0B.toInt()), 0xFFF59E0B.toInt()),
                "XiaoHeiChain 测试网", if (Api.baseUrl.contains("8222") && !Api.baseUrl.contains("18222")) "✓ 当前" else "") {
                Api.saveUrl(Nav.act, "http://127.0.0.1:8222")
                Nav.closeSheet(); MainApp.refreshAll(); Nav.toast("已切换到 XiaoHeiChain 测试网")
            },
            setItem(Nav.act, setIc(Nav.act, R.drawable.ic_chain, alpha(0x3B82F6.toInt()), C.BLUE2),
                "自定义", Api.baseUrl.removePrefix("http://")) { openCustom() }
        ))
        body.addView(g)
        Nav.sheet("网络选择", body)
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
