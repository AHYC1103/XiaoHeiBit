package com.xiaoheibit.wallets

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 交易记录列表渲染（webui renderTxs 等价） */
object TxViews {
    fun shortAddr(a: String): String =
        if (a.length <= 12) a else a.substring(0, 6) + "…" + a.substring(a.length - 4)

    private fun dayLabel(ts: Long): String {
        val sdf = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
        return sdf.format(Date(ts * 1000))
    }

    private fun kind(t: Tx, cur: String?): Triple<String, Int, Int> {
        // 返回 (类型 in/out/sys, 图标res, 主色)
        return when {
            t.sender == "SYSTEM" -> Triple("sys", R.drawable.ic_mine, C.BLUE2)
            t.receiver == cur -> Triple("in", R.drawable.ic_arrow_down, C.GREEN)
            else -> Triple("out", R.drawable.ic_arrow_up, C.RED)
        }
    }

    fun txItem(ctx: android.content.Context, t: Tx, cur: String?, height: Int): View {
        val (k, icRes, color) = kind(t, cur)
        val item = LinearLayout(ctx)
        item.gravity = Gravity.CENTER_VERTICAL
        item.background = solid(C.CARD, 20f)
        item.setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        val ilp = LinearLayout.LayoutParams(-1, -2)
        ilp.setMargins(16.dp(), 0, 16.dp(), 8.dp())
        item.layoutParams = ilp

        val icBox = LinearLayout(ctx)
        icBox.gravity = Gravity.CENTER
        val bgColor = ((color and 0x00FFFFFF) or 0x1F000000)
        icBox.background = solid(bgColor, 100f)
        icBox.layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
        icBox.addView(icon(ctx, icRes, 19, color))
        item.addView(icBox)

        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        val infLp = LinearLayout.LayoutParams(0, -2, 1f)
        infLp.marginStart = 12.dp()
        info.layoutParams = infLp
        val title = LinearLayout(ctx)
        title.gravity = Gravity.CENTER_VERTICAL
        val titleText = when {
            t.sender == "SYSTEM" -> if (t.signature == "GENESIS") "创世发行" else "挖矿奖励"
            t.sender == cur -> "转出"
            else -> "转入"
        }
        val tt = tv(ctx, titleText, 15f, C.TXT, true)
        title.addView(tt)
        val tag = TextView(ctx)
        tag.textSize = 11f
        when {
            t.pending -> { tag.text = " (待打包)"; tag.setTextColor(0xFFF59E0B.toInt()) }
            t.height > 0 -> { tag.text = " (已确认${(height - t.height).coerceAtLeast(0)}块)"; tag.setTextColor(0xFF22C55E.toInt()) }
        }
        title.addView(tag)
        info.addView(title)
        val peer = if (t.sender == "SYSTEM") "系统奖励"
        else if (t.sender == cur) "至 " + shortAddr(t.receiver)
        else "来自 " + shortAddr(t.sender)
        val meta = tv(ctx, peer, 12f, C.SUB)
        info.addView(meta)
        item.addView(info)

        val sign = if (k == "out") "-" else "+"
        val amt = tv(ctx, sign + t.amount, 15f, if (k == "out") C.RED else C.GREEN, true)
        item.addView(amt)
        return item
    }

    /** 渲染到容器（按日期分组 + 搜索过滤） */
    fun render(ctx: android.content.Context, container: LinearLayout, txs: List<Tx>,
               cur: String?, height: Int, query: String = "") {
        container.removeAllViews()
        var list = txs
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                it.sender.lowercase().contains(q) || it.receiver.lowercase().contains(q) ||
                it.amount.toString().contains(q)
            }
        }
        list = list.sortedByDescending { it.ts }
        if (list.isEmpty()) {
            val e = tv(ctx, "暂无交易记录", 14f, C.SUB)
            e.gravity = Gravity.CENTER
            e.setPadding(0, 60.dp(), 0, 60.dp())
            container.addView(e)
            return
        }
        val groups = LinkedHashMap<String, MutableList<Tx>>()
        list.forEach { groups.getOrPut(dayLabel(it.ts)) { mutableListOf() }.add(it) }
        groups.forEach { (day, items) ->
            val lab = tv(ctx, day, 13f, C.SUB)
            lab.setPadding(16.dp(), 14.dp(), 16.dp(), 6.dp())
            container.addView(lab)
            items.forEach { container.addView(txItem(ctx, it, cur, height)) }
        }
    }
}
