package com.xiaoheicoin.wallet

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 钱包主页 / 币种详情 / 交易记录 / 购买 — Gem Wallet 风格 */
object WalletPages {
    private lateinit var walletName: TextView
    private lateinit var walletAvatar: android.widget.ImageView
    private lateinit var bigBal: TextView
    private lateinit var chainInfo: TextView
    private lateinit var assAmt: TextView
    private lateinit var assCny: TextView
    private val coinBalViews = HashMap<String, TextView>()
    private val coinCnyViews = HashMap<String, TextView>()
    private val coinRows = HashMap<String, View>()
    private lateinit var coinBal: TextView
    private lateinit var coinTxBox: LinearLayout
    private lateinit var txBox: LinearLayout
    private val searchInput = arrayOfNulls<EditText>(1)
    private val buyUsd = arrayOfNulls<EditText>(1)
    private val buyXhb = arrayOfNulls<TextView>(1)
    private val buyBal = arrayOfNulls<TextView>(1)

    /** Gem 风格操作按钮：48dp 圆形 primary + 28dp 白色图标 + 下方文字 */
    private fun circleBtn(icRes: Int, label: String, onClick: () -> Unit): LinearLayout {
        val wrap = LinearLayout(Nav.act)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.gravity = Gravity.CENTER
        wrap.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val c = LinearLayout(Nav.act)
        c.gravity = Gravity.CENTER
        c.background = solid(C.BLUE, 100f)
        c.layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp())
        c.addView(icon(Nav.act, icRes, 24, C.TXT))
        c.setOnClickListener { onClick() }
        wrap.addView(c)
        val l = tv(Nav.act, label, 13f, C.CYAN)
        l.gravity = Gravity.CENTER
        l.setPadding(0, 8.dp(), 0, 0)
        wrap.addView(l)
        return wrap
    }

    /** Gem 风格币种图标圆底（44dp） */
    private fun coinIcon(ctx: android.content.Context, content: View, bgColor: Int): LinearLayout {
        val box = LinearLayout(ctx)
        box.gravity = Gravity.CENTER
        box.background = solid(bgColor, 100f)
        box.layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
        box.addView(content)
        return box
    }

    /** Gem 风格币种列表行（不是卡片，透明背景 + 分割线） */
    private fun coinRow(ctx: android.content.Context, iconView: View, name: String, sub: String,
                        onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
        row.background = solid(0x00000000, 0f)
        row.addView(iconView)
        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        val ilp = LinearLayout.LayoutParams(0, -2, 1f)
        ilp.marginStart = 12.dp()
        info.layoutParams = ilp
        info.addView(tv(ctx, name, 16f, C.TXT, true))
        info.addView(tv(ctx, sub, 13f, C.CYAN))
        row.addView(info)
        row.setOnClickListener { onClick() }
        return row
    }

    /** 给币种行添加右侧余额 */
    private fun addTrailing(row: LinearLayout, amtView: TextView, cnyView: TextView) {
        val amt = LinearLayout(Nav.act)
        amt.orientation = LinearLayout.VERTICAL
        amt.gravity = Gravity.RIGHT
        amt.addView(amtView)
        amt.addView(cnyView)
        row.addView(amt)
    }

    fun buildWallet(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        sv.addView(box)

        // topbar
        val top = LinearLayout(ctx)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(12.dp(), 38.dp(), 12.dp(), 8.dp())
        top.addView(iconBtn(ctx, R.drawable.ic_scan) { ScanUtil.open { MainApp.handleScan(it) } })
        val sel = LinearLayout(ctx)
        sel.gravity = Gravity.CENTER
        sel.setPadding(10.dp(), 0, 10.dp(), 0)
        sel.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        walletAvatar = android.widget.ImageView(ctx)
        walletAvatar.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        walletAvatar.setPadding(2.dp(), 2.dp(), 2.dp(), 2.dp())
        walletAvatar.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF4285F4.toInt(), 0xFFEA4335.toInt())
        ).apply { cornerRadius = 20.dp().toFloat() }
        walletAvatar.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        SvgLoader.load(walletAvatar, SvgLoader.path("fixed_1.svg"))
        sel.addView(walletAvatar)
        walletName = tv(ctx, "主钱包", 17f, C.TXT, true)
        walletName.setPadding(8.dp(), 0, 4.dp(), 0)
        sel.addView(walletName)
        sel.addView(icon(ctx, R.drawable.ic_chevron_down, 14, C.CYAN))
        sel.setOnClickListener { WS.wallets() }
        top.addView(sel)
        top.addView(iconBtn(ctx, R.drawable.ic_search) { Nav.go("txs") })
        box.addView(top)

        // 余额（居中）
        val balWrap = LinearLayout(ctx)
        balWrap.orientation = LinearLayout.VERTICAL
        balWrap.gravity = Gravity.CENTER
        balWrap.setPadding(20.dp(), 16.dp(), 20.dp(), 8.dp())
        val lab = tv(ctx, "总资产 (XHB)", 13f, C.CYAN)
        lab.gravity = Gravity.CENTER
        balWrap.addView(lab)
        bigBal = tv(ctx, "--", 40f, C.TXT, true)
        bigBal.gravity = Gravity.CENTER
        balWrap.addView(bigBal)
        chainInfo = tv(ctx, "高度 -- · 难度 --", 14f, C.GREEN)
        chainInfo.gravity = Gravity.CENTER
        chainInfo.setPadding(0, 6.dp(), 0, 16.dp())
        balWrap.addView(chainInfo)
        box.addView(balWrap)

        // 四个圆钮（48dp）
        val acts = LinearLayout(ctx)
        acts.setPadding(24.dp(), 0, 24.dp(), 16.dp())
        acts.addView(circleBtn(R.drawable.ic_send, "发送") { WS.send() })
        acts.addView(circleBtn(R.drawable.ic_qrcode, "接收") { WS.recv() })
        acts.addView(circleBtn(R.drawable.ic_buy, "购买") { Nav.go("buy") })
        acts.addView(circleBtn(R.drawable.ic_swap, "兑换") { Nav.go("swap") })
        box.addView(acts)

        // 币种列表（Gem 风格：每个币种独立卡片，卡片间有间距）
        val listWrap = LinearLayout(ctx)
        listWrap.orientation = LinearLayout.VERTICAL
        val llp = LinearLayout.LayoutParams(-1, -2)
        llp.setMargins(16.dp(), 4.dp(), 16.dp(), 14.dp())
        listWrap.layoutParams = llp

        // 币种图标配色
        fun coinColor(sym: String): Int = when (sym) {
            "TRX" -> 0xFFE50914.toInt()
            "USDT" -> 0xFF26A17B.toInt()
            "BNB" -> 0xFFF3BA2F.toInt()
            else -> 0xFF2D5BE6.toInt()
        }

        // XHB 行（独立卡片）
        val xhbRow = LinearLayout(ctx); xhbRow.orientation = LinearLayout.HORIZONTAL
        xhbRow.gravity = Gravity.CENTER_VERTICAL
        xhbRow.background = solid(C.CARD, 16f)
        xhbRow.setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        val xhbRowLp = LinearLayout.LayoutParams(-1, -2); xhbRowLp.bottomMargin = 8.dp()
        xhbRow.layoutParams = xhbRowLp
        val xhbLogoContent = ImageView(ctx)
        xhbLogoContent.setImageResource(R.drawable.ic_xhb)
        xhbLogoContent.scaleType = ImageView.ScaleType.FIT_CENTER
        xhbLogoContent.layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
        xhbRow.addView(xhbLogoContent)
        val xhbMid = LinearLayout(ctx); xhbMid.orientation = LinearLayout.VERTICAL
        val xhbMidLp = LinearLayout.LayoutParams(0, -2, 1f); xhbMidLp.marginStart = 12.dp(); xhbMid.layoutParams = xhbMidLp
        xhbMid.addView(tv(ctx, "XiaoHeiCoin", 15f, C.TXT, true))
        xhbMid.addView(tv(ctx, "XHB · XiaoHeiChain", 12f, C.SUB))
        xhbRow.addView(xhbMid)
        val xhbRight = LinearLayout(ctx); xhbRight.orientation = LinearLayout.VERTICAL
        assAmt = tv(ctx, "--", 16f, C.TXT, true); assAmt.gravity = Gravity.RIGHT
        assCny = tv(ctx, "≈ ¥0", 13f, C.CYAN); assCny.gravity = Gravity.RIGHT
        xhbRight.addView(assAmt); xhbRight.addView(assCny)
        xhbRow.addView(xhbRight)
        xhbRow.setOnClickListener { Nav.go("coin") }
        listWrap.addView(xhbRow)

        // 其他币种行（助记词=全链，私钥=仅导入链）
        val curW = WalletStore.current()
        val impChain = curW?.importedChain
        val showCoins = when {
            curW?.mnemonic != null -> MultiCoin.COINS.filter { !it.isNative }
            impChain == "TRX" -> MultiCoin.COINS.filter { it.symbol in setOf("TRX", "USDT") }
            impChain == "BNB" -> MultiCoin.COINS.filter { it.symbol == "BNB" }
            else -> emptyList()
        }
        for (coin in showCoins) {
            val cRow = LinearLayout(ctx); cRow.orientation = LinearLayout.HORIZONTAL
            cRow.gravity = Gravity.CENTER_VERTICAL
            cRow.background = solid(C.CARD, 16f)
            cRow.setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            val cRowLp = LinearLayout.LayoutParams(-1, -2); cRowLp.bottomMargin = 8.dp()
            cRow.layoutParams = cRowLp
            // 币种彩色图标
            val iconRes = when {
                coin.isNative -> R.drawable.ic_xhb
                coin.symbol == "TRX" -> R.drawable.ic_tron
                coin.symbol == "BNB" -> R.drawable.ic_bnb
                coin.symbol == "USDT" -> R.drawable.ic_usdt
                else -> R.drawable.ic_xhb
            }
            val cIcon = ImageView(ctx)
            cIcon.setImageResource(iconRes)
            cIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            cIcon.layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            cRow.addView(cIcon)
            val mid = LinearLayout(ctx); mid.orientation = LinearLayout.VERTICAL
            val midLp = LinearLayout.LayoutParams(0, -2, 1f); midLp.marginStart = 12.dp(); mid.layoutParams = midLp
            mid.addView(tv(ctx, coin.name, 15f, C.TXT, true))
            mid.addView(tv(ctx, "${coin.symbol} · ${coin.chain}", 12f, C.SUB))
            cRow.addView(mid)
            val right = LinearLayout(ctx); right.orientation = LinearLayout.VERTICAL
            val bal = State.coinBalances[coin.symbol] ?: 0.0
            val balTv = tv(ctx, "%.4f".format(bal), 16f, C.TXT, true); balTv.gravity = Gravity.RIGHT
            coinBalViews[coin.symbol] = balTv
            val cnyTv = tv(ctx, Rates.fmt(coin.symbol, bal), 13f, C.CYAN); cnyTv.gravity = Gravity.RIGHT
            coinCnyViews[coin.symbol] = cnyTv
            right.addView(balTv); right.addView(cnyTv)
            cRow.addView(right)
            cRow.setOnClickListener { WS.recvCoin(coin) }
            listWrap.addView(cRow)
            coinRows[coin.symbol] = cRow
        }
        box.addView(listWrap)
        return sv
    }

    fun bindWallet() {
        val c = State.cur()
        walletName.text = c?.name ?: "未连接"
        val avIc = c?.icon?.ifBlank { "" } ?: ""
        val svgFile = if (avIc.endsWith(".svg")) avIc else "fixed_1.svg"
        SvgLoader.load(walletAvatar, SvgLoader.path(svgFile))
        bigBal.text = if (Api.online) (c?.balance?.toString() ?: "0") else "离线"
        assAmt.text = c?.balance?.toString() ?: "0"
        assCny.text = Rates.fmt("XHB", (c?.balance ?: 0).toDouble())
        chainInfo.text = "高度 ${State.height} · 难度 ${State.difficulty}"
        chainInfo.setTextColor(if (Api.online) C.GREEN else C.RED)
        val curW2 = WalletStore.current()
        val impChain2 = curW2?.importedChain
        val visibleCoins = when {
            curW2?.mnemonic != null -> setOf("TRX", "BNB", "USDT")
            impChain2 == "TRX" -> setOf("TRX", "USDT")
            impChain2 == "BNB" -> setOf("BNB")
            else -> emptySet()
        }
        coinRows.forEach { (sym, v) -> v.visibility = if (sym in visibleCoins) View.VISIBLE else View.GONE }
        coinBalViews.forEach { (symbol, tv) ->
            val bal = State.coinBalances[symbol] ?: 0.0
            tv.text = "%.4f".format(bal)
        }
        coinCnyViews.forEach { (symbol, tv) ->
            val bal = State.coinBalances[symbol] ?: 0.0
            tv.text = Rates.fmt(symbol, bal)
        }
    }

    fun buildCoin(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "") { Nav.go("wallet") })

        val balLab = tv(ctx, "余额", 14f, C.CYAN)
        balLab.gravity = Gravity.CENTER
        balLab.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 16.dp(); bottomMargin = 4.dp()
        }
        box.addView(balLab)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        row.layoutParams = LinearLayout.LayoutParams(-1, -2)
        coinBal = tv(ctx, "0", 36f, C.TXT, true)
        row.addView(coinBal)
        val unit = tv(ctx, " XHB", 16f, C.CYAN)
        row.addView(unit)
        box.addView(row)
        val noquote = tv(ctx, Rates.fmt("XHB", 1.0) + " / XHB", 13f, C.CYAN)
        noquote.gravity = Gravity.CENTER
        noquote.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 8.dp(); bottomMargin = 20.dp()
        }
        box.addView(noquote)

        val contract = LinearLayout(ctx)
        contract.gravity = Gravity.CENTER_VERTICAL
        contract.background = solid(C.CARD, 16f)
        contract.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        val ctlp = LinearLayout.LayoutParams(-1, -2)
        ctlp.setMargins(16.dp(), 0, 16.dp(), 16.dp())
        contract.layoutParams = ctlp
        val cm = LinearLayout(ctx)
        cm.orientation = LinearLayout.VERTICAL
        cm.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        cm.addView(tv(ctx, "合约地址", 13f, C.CYAN))
        cm.addView(tv(ctx, "0000000000000000", 14f, C.TXT))
        contract.addView(cm)
        contract.addView(tv(ctx, "›", 20f, C.CYAN))
        box.addView(contract)

        box.addView(tv(ctx, "交易记录", 15f, C.TXT, true).apply {
            setPadding(16.dp(), 4.dp(), 16.dp(), 8.dp())
        })
        coinTxBox = LinearLayout(ctx)
        coinTxBox.orientation = LinearLayout.VERTICAL
        box.addView(coinTxBox)
        return sv
    }
    fun bindCoin() {
        coinBal.text = (State.cur()?.balance ?: 0).toString()
        coinTxBox.removeAllViews()
        State.txs.sortedByDescending { it.ts }.take(10).forEach {
            coinTxBox.addView(TxViews.txItem(Nav.act, it, State.current, State.height))
        }
    }

    fun buildTxs(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "交易记录") { Nav.go("wallet") })
        val search = LinearLayout(ctx)
        search.gravity = Gravity.CENTER_VERTICAL
        search.background = solid(C.CARD, 16f)
        search.setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        val slp = LinearLayout.LayoutParams(-1, -2)
        slp.setMargins(16.dp(), 4.dp(), 16.dp(), 8.dp())
        search.layoutParams = slp
        search.addView(icon(ctx, R.drawable.ic_search, 17, C.CYAN))
        val e = EditText(ctx)
        e.hint = "搜索地址/金额/类型"
        e.setHintTextColor(C.CYAN)
        e.setTextColor(C.TXT)
        e.textSize = 14f
        e.background = null
        e.setPadding(10.dp(), 0, 0, 0)
        e.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        e.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { bindTxs(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        search.addView(e)
        searchInput[0] = e
        box.addView(search)
        txBox = LinearLayout(ctx)
        txBox.orientation = LinearLayout.VERTICAL
        box.addView(txBox)
        return sv
    }
    fun bindTxs(q: String = searchInput[0]?.text?.toString() ?: "") {
        if (::txBox.isInitialized) TxViews.render(Nav.act, txBox, State.txs, State.current, State.height, q)
    }

    fun buildBuy(): View {
        val ctx = Nav.act
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        val top = LinearLayout(ctx)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(12.dp(), 38.dp(), 12.dp(), 8.dp())
        val back = tv(ctx, "‹", 30f, C.TXT)
        back.gravity = Gravity.CENTER
        back.layoutParams = LinearLayout.LayoutParams(40.dp(), 44.dp())
        back.setOnClickListener { Nav.go("wallet") }
        top.addView(back)
        val seg = LinearLayout(ctx)
        seg.background = solid(C.CARD, 16f)
        seg.setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        seg.gravity = Gravity.CENTER
        val buyT = TextView(ctx)
        val sellT = TextView(ctx)
        fun segStyle(t: TextView, active: Boolean) {
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
            if (active) {
                t.background = blueGrad(10f)
                t.setTextColor(android.graphics.Color.WHITE)
            } else {
                t.background = null
                t.setTextColor(C.CYAN)
            }
        }
        buyT.text = "购买"; sellT.text = "出售"
        buyT.setOnClickListener { segStyle(buyT, true); segStyle(sellT, false) }
        sellT.setOnClickListener {
            segStyle(buyT, false); segStyle(sellT, true)
            Nav.toast("出售功能开发中")
        }
        seg.addView(buyT); seg.addView(sellT)
        segStyle(buyT, true); segStyle(sellT, false)
        val stlp = LinearLayout.LayoutParams(0, -2, 1f)
        stlp.gravity = Gravity.CENTER
        seg.layoutParams = stlp
        top.addView(seg)
        val ph = View(ctx)
        ph.layoutParams = LinearLayout.LayoutParams(40.dp(), 44.dp())
        top.addView(ph)
        box.addView(top)

        val usdRow = LinearLayout(ctx)
        usdRow.gravity = Gravity.CENTER
        usdRow.setPadding(20.dp(), 30.dp(), 20.dp(), 4.dp())
        val dollar = tv(ctx, "￥", 36f, C.CYAN)
        usdRow.addView(dollar)
        val usd = EditText(ctx)
        usd.textSize = 60f
        usd.setTextColor(C.TXT)
        usd.background = null
        usd.hint = "0"
        usd.setHintTextColor(C.CYAN)
        usd.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        usd.layoutParams = LinearLayout.LayoutParams(-2, -2)
        buyUsd[0] = usd
        usdRow.addView(usd)
        box.addView(usdRow)
        val xhb = tv(ctx, "≈ 0.00 XHB", 14f, C.CYAN)
        xhb.gravity = Gravity.CENTER
        xhb.setPadding(0, 4.dp(), 0, 24.dp())
        box.addView(xhb)
        buyXhb[0] = xhb
        usd.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.toDoubleOrNull() ?: 0.0
                buyXhb[0]?.let { it.text = "≈ %.2f XHB".format(v / Rates.cny("XHB")) }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val asset = LinearLayout(ctx)
        asset.gravity = Gravity.CENTER_VERTICAL
        asset.background = solid(C.CARD, 16f)
        asset.setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        val alp = LinearLayout.LayoutParams(-1, -2)
        alp.setMargins(16.dp(), 0, 16.dp(), 12.dp())
        asset.layoutParams = alp
        val logo = ImageView(ctx)
        logo.setImageResource(R.drawable.ic_xhb)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        logo.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        asset.addView(logo)
        val am = LinearLayout(ctx)
        am.orientation = LinearLayout.VERTICAL
        am.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        am.setPadding(12.dp(), 0, 0, 0)
        am.addView(tv(ctx, "XHB", 15f, C.TXT, true))
        val bb = tv(ctx, "我的余额: 0", 12f, C.CYAN)
        am.addView(bb)
        buyBal[0] = bb
        asset.addView(am)
        val b100 = TextView(ctx)
        b100.text = "CNY￥100"
        b100.setTextColor(C.BLUE)
        b100.textSize = 14f
        b100.setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
        b100.background = solid(C.CARD2, 10f)
        b100.setOnClickListener { usd.setText("100") }
        asset.addView(b100)
        box.addView(asset)

        val cont = primaryBtn(ctx, "继续") { Nav.toast("购买功能开发中") }
        val space = View(ctx)
        space.layoutParams = LinearLayout.LayoutParams(-1, 20.dp())
        box.addView(space)
        box.addView(cont)
        return sv
    }
    fun bindBuy() { buyBal[0]?.let { it.text = "我的余额: ${State.cur()?.balance ?: 0}" } }
}
