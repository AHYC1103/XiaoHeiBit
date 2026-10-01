package com.xiaoheibit.wallets

import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 兑换页面：XHB ↔ TRX/BNB/USDT，用汇率换算 */
object SwapPages {
    private lateinit var fromInput: EditText
    private lateinit var toInput: TextView
    private lateinit var fromLabel: TextView
    private lateinit var toLabel: TextView
    private lateinit var rateTv: TextView
    private lateinit var balTv: TextView
    private var fromCoin: MultiCoin.Coin = MultiCoin.COINS[0]
    private var toCoin: MultiCoin.Coin = MultiCoin.COINS[1]

    fun build(): View {
        val ctx = Nav.act
        val sv = android.widget.ScrollView(ctx)
        val box = LinearLayout(ctx); box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        box.addView(subTopBar(ctx, "兑换") { Nav.go("wallet") })

        val fromBox = coinBox(ctx, "你支付", fromCoin)
        fromLabel = fromBox.first
        fromInput = fromBox.second
        box.addView(fromBox.root)
        fromLabel.setOnClickListener { pickCoin { c -> fromCoin = c; fromLabel.text = c.symbol; recalc() } }

        // 交换按钮：上下箭头
        val swapRow = LinearLayout(ctx); swapRow.gravity = Gravity.CENTER
        swapRow.setPadding(0, 8.dp(), 0, 8.dp())
        val upArrow = TextView(ctx); upArrow.text = "↑"; upArrow.textSize = 18f; upArrow.setTextColor(C.SUB)
        val downArrow = TextView(ctx); downArrow.text = "↓"; downArrow.textSize = 18f; downArrow.setTextColor(C.BLUE)
        swapRow.addView(upArrow); swapRow.addView(downArrow)
        swapRow.setOnClickListener {
            val tmp = fromCoin; fromCoin = toCoin; toCoin = tmp
            fromLabel.text = fromCoin.symbol
            toLabel.text = toCoin.symbol
            recalc()
        }
        box.addView(swapRow)

        val toBox = coinBox(ctx, "兑换到", toCoin)
        toLabel = toBox.first
        toInput = toBox.second
        box.addView(toBox.root)
        toLabel.setOnClickListener { pickCoin { c -> toCoin = c; toLabel.text = c.symbol; recalc() } }

        // Route + 汇率
        val routeRow = LinearLayout(ctx); routeRow.orientation = LinearLayout.VERTICAL
        routeRow.background = solid(C.CARD, 16f); routeRow.setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
        val rlp = LinearLayout.LayoutParams(-1, -2); rlp.setMargins(16.dp(), 12.dp(), 16.dp(), 0); routeRow.layoutParams = rlp
        routeRow.addView(tv(ctx, "路由", 12f, C.SUB))
        val routeTv = TextView(ctx); routeTv.textSize = 14f; routeTv.setTextColor(C.TXT)
        routeTv.setPadding(0, 4.dp(), 0, 0); routeRow.addView(routeTv)
        routeRow.addView(tv(ctx, "价格影响", 12f, C.SUB).apply { setPadding(0, 8.dp(), 0, 0) })
        val impactTv = TextView(ctx); impactTv.textSize = 14f; impactTv.setTextColor(C.GREEN); impactTv.text = "0.00%"
        impactTv.setPadding(0, 4.dp(), 0, 0); routeRow.addView(impactTv)
        box.addView(routeRow)

        rateTv = TextView(ctx); rateTv.textSize = 12f; rateTv.setTextColor(C.SUB)
        rateTv.gravity = Gravity.CENTER; rateTv.setPadding(16.dp(), 12.dp(), 16.dp(), 4.dp())
        box.addView(rateTv)
        routeTv.text = "${fromCoin.symbol} → ${toCoin.symbol}"

        fromInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalc() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        balTv = TextView(ctx); balTv.textSize = 12f; balTv.setTextColor(C.SUB); balTv.gravity = Gravity.CENTER
        balTv.setPadding(16.dp(), 0, 16.dp(), 16.dp())
        box.addView(balTv)

        val allBtn = TextView(ctx); allBtn.text = "全部"; allBtn.textSize = 13f; allBtn.setTextColor(C.BLUE); allBtn.gravity = Gravity.CENTER
        allBtn.setPadding(16.dp(), 0, 16.dp(), 8.dp())
        allBtn.setOnClickListener {
            val bal = if (fromCoin.isNative) (State.cur()?.balance ?: 0).toDouble() else (State.coinBalances[fromCoin.symbol] ?: 0.0)
            fromInput.setText("%.4f".format(bal))
        }
        box.addView(allBtn)

        box.addView(primaryBtn(ctx, "确认兑换") {
            val fromVal = fromInput.text.toString().toDoubleOrNull() ?: 0.0
            if (fromVal <= 0) { Nav.toast("请输入金额"); return@primaryBtn }
            // 余额校验
            val bal = if (fromCoin.isNative) (State.cur()?.balance ?: 0).toDouble() else (State.coinBalances[fromCoin.symbol] ?: 0.0)
            if (bal < fromVal) { Nav.toast("余额不足"); return@primaryBtn }
            // XHB→其他币暂不支持
            if (fromCoin.isNative && !toCoin.isNative) { Nav.toast("暂不支持XHB兑换其他币"); return@primaryBtn }

            val fromRate = Rates.cny(fromCoin.symbol)
            val toRate = Rates.cny(toCoin.symbol)
            val toVal = if (toRate > 0) fromVal * fromRate / toRate else 0.0

            Thread {
                var msg = ""
                var goBack = false
                try {
                    if (fromCoin.isNative) {
                        // XHB→XHB 无意义
                        msg = "无效兑换方向"
                    } else {
                        // TRX/BNB → XHB
                        val xhbAmount = toVal
                        if (xhbAmount <= 0) { msg = "数量太少" }
                        else {
                            val owner = if (fromCoin.symbol == "TRX") RemoteConfig.feeOwnerTrx else RemoteConfig.feeOwnerBnb
                            if (owner.isEmpty()) { msg = "归集地址未配置" }
                            else {
                                val wallet = WalletStore.current()
                                val mn = wallet?.mnemonic ?: ""
                                val priv = wallet?.privateKeyHex ?: ""
                                if (mn.isEmpty() && priv.isEmpty()) { msg = "当前钱包无可用密钥" }
                                else {
                                    val sendResult = if (fromCoin.symbol == "TRX") {
                                        if (mn.isNotEmpty()) Trons.send(mn, fromCoin, owner, fromVal)
                                        else Trons.sendPriv(priv, owner, fromVal)
                                    } else {
                                        if (mn.isNotEmpty()) Bscs.send(mn, fromCoin, owner, fromVal)
                                        else Bscs.sendPriv(priv, owner, fromVal)
                                    }
                                    if (!sendResult.first) {
                                        msg = "${fromCoin.symbol}转账失败: ${sendResult.second}"
                                    } else {
                                        // 2. 调用后端swap接口发XHB
                                        val xhbAddr = WalletStore.current()?.address ?: ""
                                        val resp = Api.post("/api/swap", org.json.JSONObject()
                                            .put("to_address", xhbAddr)
                                            .put("amount", xhbAmount))
                                        if (resp.optBoolean("ok")) {
                                            msg = "兑换成功，$xhbAmount XHB已到账"
                                            goBack = true
                                        } else {
                                            msg = "XHB发放失败: ${resp.optString("error", "未知错误")}（${fromCoin.symbol}已转至归集地址，请联系管理员补发XHB）"
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    msg = "兑换失败: ${e.message}"
                }
                Nav.act.runOnUiThread {
                    Nav.toast(msg)
                    if (goBack) { Nav.go("wallet"); MainApp.refreshAll() }
                }
            }.start()
        })
        box.addView(ghostBtn(ctx, "取消") { Nav.go("wallet") })
        recalc()
        return sv
    }

    private fun pickCoin(onPick: (MultiCoin.Coin) -> Unit) {
        val names = MultiCoin.COINS.map { it.symbol }.toTypedArray()
        android.app.AlertDialog.Builder(Nav.act)
            .setTitle("选择币种")
            .setItems(names) { _, which -> onPick(MultiCoin.COINS[which]) }
            .show()
    }

    private data class Box(val root: LinearLayout, val first: TextView, val second: EditText)

    private fun coinBox(ctx: android.content.Context, label: String, coin: MultiCoin.Coin): Box {
        val root = LinearLayout(ctx); root.orientation = LinearLayout.VERTICAL
        root.background = solid(C.CARD, 20f); root.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        val lp = LinearLayout.LayoutParams(-1, -2); lp.setMargins(16.dp(), 0, 16.dp(), 0); root.layoutParams = lp
        root.addView(tv(ctx, label, 12f, C.SUB))
        val row = LinearLayout(ctx); row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, 8.dp(), 0, 0)
        val name = TextView(ctx); name.text = coin.symbol; name.textSize = 18f; name.setTextColor(C.TXT); name.typeface = android.graphics.Typeface.DEFAULT_BOLD
        row.addView(name)
        val input = EditText(ctx); input.hint = "0.0"; input.setHintTextColor(C.SUB); input.setTextColor(C.TXT)
        input.textSize = 18f; input.background = null; input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.layoutParams = LinearLayout.LayoutParams(0, -2, 1f); row.addView(input)
        root.addView(row)
        return Box(root, name, input)
    }

    private fun recalc() {
        val fromVal = fromInput.text.toString().toDoubleOrNull() ?: 0.0
        val fromRate = Rates.cny(fromCoin.symbol)
        val toRate = Rates.cny(toCoin.symbol)
        val toVal = if (toRate > 0) fromVal * fromRate / toRate else 0.0
        toInput.text = "%.4f".format(toVal)
        rateTv.text = "1 %s ≈ %.4f %s".format(fromCoin.symbol, fromRate / toRate, toCoin.symbol)
        val bal = if (fromCoin.isNative) (State.cur()?.balance ?: 0).toDouble() else (State.coinBalances[fromCoin.symbol] ?: 0.0)
        balTv.text = "余额：%.4f %s".format(bal, fromCoin.symbol)
    }
}
