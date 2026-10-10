package com.xiaoheicoin.wallet

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** NFT 列表 / 详情 / 铸币 / 发送(一次性码+直接地址) / 接收 */
object NftPages {
    var currentId = -1
    private var prefillCode = ""
    private lateinit var grid: LinearLayout
    private lateinit var detailImg: FrameLayout
    private lateinit var dName: TextView; private lateinit var dId: TextView
    private lateinit var dSupply: TextView; private lateinit var dCid: TextView
    private lateinit var mintName: EditText; private lateinit var mintCid: EditText
    private lateinit var mintSupply: EditText; private lateinit var mintDesc: EditText
    private lateinit var sendQr: ImageView; private lateinit var sendCodeBox: TextView
    private lateinit var sendTabCode: LinearLayout; private lateinit var sendTabAddr: LinearLayout
    private var boundCid = ""
    private lateinit var tabCode: TextView; private lateinit var tabAddr: TextView
    private lateinit var sendAddr: EditText
    private lateinit var recvCode: EditText

    fun prefillRecv(code: String) { prefillCode = code }

    private fun nftImage(cid: String, px: Int): FrameLayout {
        val f = FrameLayout(Nav.act)
        f.setBackgroundColor(C.CARD2)
        val iv = ImageView(Nav.act); iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.layoutParams = FrameLayout.LayoutParams(-1, -1)
        f.addView(iv)
        val em = TextView(Nav.act); em.text = "🖼️"; em.textSize = 30f; em.gravity = Gravity.CENTER
        em.layoutParams = FrameLayout.LayoutParams(-1, -1); f.addView(em)
        Nav.loadImg(Nav.ipfs(cid), iv) { ok -> if (ok) em.visibility = View.GONE }
        f.layoutParams = LinearLayout.LayoutParams(px, px)
        return f
    }

    fun buildList(): View {
        val ctx = Nav.act
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.addView(subTopBar(ctx, "我的NFT") { Nav.go("more") })
        val sv = ScrollView(ctx); val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box); sv.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        grid = LinearLayout(ctx); grid.orientation = LinearLayout.VERTICAL
        grid.setPadding(16.dp(), 4.dp(), 16.dp(), 16.dp())
        box.addView(grid)
        root.addView(sv)
        val bottom = LinearLayout(ctx); bottom.setPadding(20.dp(), 8.dp(), 20.dp(), 12.dp())
        val recv = ghostBtn(ctx, "接收NFT") { Nav.go("nftRecv") }
        recv.background = solid(C.CARD2, 16f, 1, C.LINE)
        val mint = ghostBtn(ctx, "铸造NFT") { Nav.go("nftMint") }
        mint.background = blueGrad(20f)
        bottom.addView(recv); bottom.addView(mint)
        root.addView(bottom)
        return root
    }
    fun bindList() {
        Nav.io {
            State.refreshNfts()
            Nav.ui {
                grid.removeAllViews()
                if (State.nfts.isEmpty()) {
                    val e = tv(Nav.act, "暂无NFT\n点击下方按钮接收或铸造", 14f, C.SUB)
                    e.gravity = Gravity.CENTER; e.setPadding(0, 60.dp(), 0, 60.dp())
                    grid.addView(e); return@ui
                }
                val items = State.nfts
                var i = 0
                while (i < items.size) {
                    val row = LinearLayout(Nav.act)
                    val rlp = LinearLayout.LayoutParams(-1, -2); rlp.bottomMargin = 12.dp(); row.layoutParams = rlp
                    for (j in 0..1) {
                        if (i + j < items.size) {
                            val n = items[i + j]
                            val card = LinearLayout(Nav.act); card.orientation = LinearLayout.VERTICAL
                            card.background = solid(C.CARD, 16f); card.clipToOutline = true
                            val lp = LinearLayout.LayoutParams(0, -2, 1f)
                            lp.setMargins(if (j == 0) 0 else 6.dp(), 0, if (j == 0) 6.dp() else 0, 0)
                            card.layoutParams = lp
                            val px = (Nav.act.resources.displayMetrics.widthPixels - 32.dp() - 12.dp()) / 2
                            card.addView(nftImage(n.optString("cid"), px))
                            val info = LinearLayout(Nav.act); info.orientation = LinearLayout.VERTICAL
                            info.setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
                            info.addView(tv(Nav.act, "#${n.optInt("id")} ${n.optString("name")}", 14f, C.TXT, true))
                            val s = tv(Nav.act, "发行量 ${n.optInt("supply")}", 12f, C.SUB); s.setPadding(0, 2.dp(), 0, 0)
                            info.addView(s)
                            card.addView(info)
                            val id = n.optInt("id")
                            card.setOnClickListener { openDetail(id) }
                            row.addView(card)
                        } else {
                            val ph = View(Nav.act); ph.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); row.addView(ph)
                        }
                    }
                    grid.addView(row); i += 2
                }
            }
        }
    }

    private fun openDetail(id: Int) {
        currentId = id; Nav.go("nftDetail")
    }

    fun buildDetail(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx); val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "NFT详情") { Nav.go("nft") })
        val pad = LinearLayout(ctx); pad.orientation = LinearLayout.VERTICAL; pad.setPadding(16.dp(), 0, 16.dp(), 0)
        val px = Nav.act.resources.displayMetrics.widthPixels - 32.dp()
        detailImg = FrameLayout(ctx); detailImg.background = solid(C.CARD, 16f)
        detailImg.clipToOutline = true
        detailImg.layoutParams = LinearLayout.LayoutParams(px, px)
        pad.addView(detailImg)
        dName = tv(ctx, "--", 18f, C.TXT).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 16.dp(), 0, 0) }
        pad.addView(dName)
        dId = tv(ctx, "#1", 13f, C.SUB); dId.setPadding(0, 6.dp(), 0, 0); pad.addView(dId)
        dSupply = tv(ctx, "发行量：1", 13f, C.SUB); pad.addView(dSupply)
        dCid = tv(ctx, "", 13f, C.SUB); dCid.setPadding(0, 8.dp(), 0, 0); pad.addView(dCid)
        val btns = LinearLayout(ctx); btns.setPadding(0, 24.dp(), 0, 0)
        val send = ghostBtn(ctx, "发送") { goSend() }; send.background = blueGrad(20f); send.setTextColor(android.graphics.Color.WHITE)
        val sell = ghostBtn(ctx, "出售") { Nav.toast("出售功能开发中") }
        sell.background = solid(C.CARD2, 16f, 1, C.LINE); sell.alpha = 0.5f
        btns.addView(send); btns.addView(sell)
        pad.addView(btns)
        box.addView(pad)
        return sv
    }
    fun bindDetail() {
        val n = State.nfts.find { it.optInt("id") == currentId } ?: return
        dName.text = n.optString("name")
        dId.text = "#${n.optInt("id")}"
        dSupply.text = "发行量：${n.optInt("supply")}"
        dCid.text = "ipts: ${n.optString("cid")}"
        val cid = n.optString("cid")
        if (cid == boundCid) return          // 轮询时图片不变，避免 removeAllViews 重绘闪烁
        boundCid = cid
        detailImg.removeAllViews()
        val px = Nav.act.resources.displayMetrics.widthPixels - 32.dp()
        val f = nftImage(cid, px)
        // nftImage 自带 lp，迁移视图
        val iv = f.getChildAt(0); val em = f.getChildAt(1)
        (iv.parent as? ViewGroup)?.removeView(iv); (em.parent as? ViewGroup)?.removeView(em)
        detailImg.addView(iv); detailImg.addView(em)
    }

    fun buildMint(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx); val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "制作NFT") { Nav.go("nft") })
        val nF = field(ctx, "名称", "给NFT起个名字"); mintName = nF.input
        val cF = field(ctx, "图片(ipts)", "图片的ipts信息"); mintCid = cF.input
        val sF = field(ctx, "发行量", "1", numeric = true); mintSupply = sF.input; mintSupply.setText("1")
        val dF = field(ctx, "描述（可选）", "描述一下这个NFT"); mintDesc = dF.input
        listOf(nF.root, cF.root, sF.root, dF.root).forEach { box.addView(it) }
        box.addView(primaryBtn(ctx, "铸造") {
            val name = mintName.text.toString().trim(); val cid = mintCid.text.toString().trim()
            val supply = mintSupply.text.toString().trim().toIntOrNull() ?: 1
            if (name.isBlank()) { Nav.toast("请输入名称"); return@primaryBtn }
            if (cid.isBlank()) { Nav.toast("请输入图片CID"); return@primaryBtn }
            val addr = State.current ?: run { Nav.toast("没有钱包"); return@primaryBtn }
            Nav.io { try {
                Api.nftMint(addr, name, cid, supply, mintDesc.text.toString().trim())
                State.refreshNfts()
                Nav.ui { Nav.toast("铸造成功"); Nav.go("nft") }
            } catch (e: Exception) { Nav.ui { Nav.toast("铸造失败: ${e.message}") } } }
        })
        return sv
    }

    fun buildSend(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx); val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "发送NFT") { Nav.go("nftDetail") })
        val pad = LinearLayout(ctx); pad.orientation = LinearLayout.VERTICAL; pad.setPadding(16.dp(), 0, 16.dp(), 0)
        val tabs = LinearLayout(ctx); tabs.setPadding(0, 8.dp(), 0, 16.dp())
        tabCode = tabBtn(ctx, "一次性码", true); tabAddr = tabBtn(ctx, "直接发地址", false)
        tabCode.setOnClickListener { switchSend(true) }; tabAddr.setOnClickListener { switchSend(false) }
        tabs.addView(tabCode); tabs.addView(tabAddr); pad.addView(tabs)

        sendTabCode = LinearLayout(ctx); sendTabCode.orientation = LinearLayout.VERTICAL
        sendQr = ImageView(ctx); sendQr.setBackgroundColor(0xFFFFFFFF.toInt()); sendQr.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        val qlp = LinearLayout.LayoutParams(240.dp(), 240.dp()); qlp.gravity = Gravity.CENTER_HORIZONTAL; sendQr.layoutParams = qlp
        sendTabCode.addView(sendQr)
        sendCodeBox = TextView(ctx); sendCodeBox.textSize = 16f; sendCodeBox.typeface = android.graphics.Typeface.DEFAULT_BOLD
        sendCodeBox.setTextColor(C.TXT); sendCodeBox.background = solid(C.CARD, 16f); sendCodeBox.setPadding(20.dp(), 20.dp(), 20.dp(), 20.dp())
        val cbp = LinearLayout.LayoutParams(-1, -2); cbp.setMargins(0, 16.dp(), 0, 0); sendCodeBox.layoutParams = cbp
        sendTabCode.addView(sendCodeBox)
        val tip = tv(ctx, "让接收方扫码或输入上面的码\n码用一次就失效，每次发送自动重新生成", 13f, C.SUB)
        tip.setPadding(0, 12.dp(), 0, 0); sendTabCode.addView(tip)
        val copy = TextView(ctx); copy.text = "复制码"; copy.gravity = Gravity.CENTER; copy.setTextColor(C.TXT); copy.textSize = 15f
        copy.background = solid(C.CARD2, 16f, 1, C.LINE); copy.setPadding(13.dp(), 13.dp(), 13.dp(), 13.dp())
        val copylp = LinearLayout.LayoutParams(-1, -2); copylp.setMargins(0, 16.dp(), 0, 0); copy.layoutParams = copylp
        copy.setOnClickListener { WS.copy(sendCodeBox.text.toString()) }
        sendTabCode.addView(copy)
        pad.addView(sendTabCode)

        sendTabAddr = LinearLayout(ctx); sendTabAddr.orientation = LinearLayout.VERTICAL
        val aF = field(ctx, "对方地址", "输入接收方钱包地址"); sendAddr = aF.input
        sendTabAddr.addView(aF.root)
        val go = primaryBtn(ctx, "确认发送") {
            val to = sendAddr.text.toString().trim()
            if (to.isBlank()) { Nav.toast("请输入对方地址"); return@primaryBtn }
            val addr = State.current ?: run { Nav.toast("没有钱包"); return@primaryBtn }
            Nav.io { try { Api.nftTransfer(addr, currentId, to); State.refreshNfts()
                Nav.ui { Nav.toast("发送成功"); Nav.go("nft") }
            } catch (e: Exception) { Nav.ui { Nav.toast("发送失败: ${e.message}") } } }
        }
        sendTabAddr.addView(go)
        sendTabAddr.visibility = View.GONE
        pad.addView(sendTabAddr)
        box.addView(pad)
        return sv
    }
    private fun tabBtn(ctx: android.content.Context, t: String, active: Boolean): TextView {
        val b = TextView(ctx); b.text = t; b.gravity = Gravity.CENTER; b.textSize = 14f; b.setTextColor(if (active) C.TXT else C.TXT)
        b.background = if (active) blueGrad(10f) else solid(C.CARD, 10f)
        b.setPadding(10.dp(), 12.dp(), 10.dp(), 12.dp()); b.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        return b
    }
    private fun switchSend(code: Boolean) {
        sendTabCode.visibility = if (code) View.VISIBLE else View.GONE
        sendTabAddr.visibility = if (code) View.GONE else View.VISIBLE
        tabCode.background = if (code) blueGrad(10f) else solid(C.CARD, 10f)
        tabAddr.background = if (code) solid(C.CARD, 10f) else blueGrad(10f)
    }
    private var lastFullCode = ""
    private fun goSend() {
        Nav.go("nftSend"); switchSend(true)
        sendCodeBox.text = "生成中..."; sendQr.setImageBitmap(null)
        val addr = State.current ?: run { Nav.ui { sendCodeBox.text = "没有钱包" }; return }
        Nav.io { try {
            val r = Api.nftSend(addr, currentId)
            val code = r.optString("code"); lastFullCode = code
            val show = code.split("$").getOrNull(2) ?: code
            val bmp = QrCode.toBitmap(code, 8, 4)
            Nav.ui { sendCodeBox.text = show; sendQr.setImageBitmap(bmp) }
        } catch (e: Exception) { Nav.ui { sendCodeBox.text = "生成失败: ${e.message}" } } }
    }

    fun buildRecv(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx); val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "接收NFT") { Nav.go("more") })
        val pad = LinearLayout(ctx); pad.orientation = LinearLayout.VERTICAL; pad.setPadding(0, 8.dp(), 0, 0)
        val f = field(ctx, "发送码", "输入或扫描发送码"); recvCode = f.input; pad.addView(f.root)
        val scan = TextView(ctx); scan.text = "📷 扫码"; scan.gravity = Gravity.CENTER; scan.setTextColor(C.TXT); scan.textSize = 15f
        scan.background = solid(C.CARD2, 16f, 1, C.LINE); scan.setPadding(13.dp(), 13.dp(), 13.dp(), 13.dp())
        val slp = LinearLayout.LayoutParams(-1, -2); slp.setMargins(16.dp(), 8.dp(), 16.dp(), 0); scan.layoutParams = slp
        scan.setOnClickListener { ScanUtil.open { c -> recvCode.setText(if (c.startsWith("XiaoHeiCoin")) c else c) } }
        box.addView(pad); box.addView(scan)
        box.addView(primaryBtn(ctx, "确认接收") {
            val code = recvCode.text.toString().trim()
            if (code.isBlank()) { Nav.toast("请输入发送码"); return@primaryBtn }
            Nav.io { try { Api.nftRecv(code); State.refreshNfts()
                Nav.ui { Nav.toast("接收成功"); Nav.go("nft") }
            } catch (e: Exception) { Nav.ui { Nav.toast("接收失败: ${e.message}") } } }
        })
        return sv
    }
    fun bindRecv() {
        if (prefillCode.isNotBlank()) { recvCode.setText(prefillCode); prefillCode = "" }
    }
}
