package com.xiaoheicoin.wallet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** 圆角实心背景 */
fun solid(color: Int, radiusDp: Float, strokeW: Int = 0, strokeColor: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusDp.dp().toFloat()
    if (strokeW > 0) g.setStroke(strokeW.dp(), strokeColor)
    return g
}
/** Gem 风格 Filled 按钮（大圆角） */
fun blueGrad(radiusDp: Float = 24f): GradientDrawable {
    return solid(C.BLUE, radiusDp)
}
fun LinearLayout.margins(l: Int, t: Int, r: Int, b: Int): LinearLayout {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(-1, -2)
    lp.setMargins(l.dp(), t.dp(), r.dp(), b.dp())
    layoutParams = lp
    return this
}
fun View.setMarginsPx(l: Int, t: Int, r: Int, b: Int) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: ViewGroup.MarginLayoutParams(-1, -2)
    lp.setMargins(l, t, r, b)
    layoutParams = lp
}

/** 线性图标（vector，tint 着色，对应 webui mask currentColor） */
fun icon(ctx: Context, res: Int, sizeDp: Int = 22, color: Int = C.TXT): ImageView {
    val iv = ImageView(ctx)
    iv.setImageResource(res)
    iv.setColorFilter(color)
    val s = sizeDp.dp()
    iv.layoutParams = LinearLayout.LayoutParams(s, s)
    return iv
}

fun tv(ctx: Context, text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextColor(color)
    t.textSize = sizeSp
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

/** 44dp 圆角方形图标按钮 */
fun iconBtn(ctx: Context, res: Int, onClick: () -> Unit): LinearLayout {
    val b = LinearLayout(ctx)
    b.gravity = Gravity.CENTER
    b.background = solid(C.CARD2, 14f)
    val lp = LinearLayout.LayoutParams(44.dp(), 44.dp())
    b.layoutParams = lp
    b.addView(icon(ctx, res, 22, C.TXT))
    b.setOnClickListener { onClick() }
    return b
}

/** 子页面顶部栏：‹ 返回 + 居中标题 + 右侧可选 */
fun subTopBar(ctx: Context, title: String, right: View? = null, onBack: () -> Unit): LinearLayout {
    val bar = LinearLayout(ctx)
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(12.dp(), 38.dp(), 12.dp(), 8.dp())
    val back = TextView(ctx)
    back.text = "‹"
    back.setTextColor(C.TXT)
    back.textSize = 30f
    back.gravity = Gravity.CENTER
    val blp = LinearLayout.LayoutParams(40.dp(), 44.dp())
    back.layoutParams = blp
    back.setOnClickListener { onBack() }
    bar.addView(back)
    val t = tv(ctx, title, 17f, C.TXT, true)
    t.gravity = Gravity.CENTER
    val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    t.layoutParams = tlp
    bar.addView(t)
    val ph = View(ctx)
    ph.layoutParams = LinearLayout.LayoutParams(40.dp(), 44.dp())
    if (right != null) bar.addView(right) else bar.addView(ph)
    return bar
}

/** Gem MainActionButton：高48dp、圆角20dp、18sp */
fun primaryBtn(ctx: Context, text: String, onClick: () -> Unit): TextView {
    val b = TextView(ctx)
    b.text = text
    b.setTextColor(Color.WHITE)
    b.textSize = 18f
    b.typeface = Typeface.DEFAULT_BOLD
    b.gravity = Gravity.CENTER
    b.background = blueGrad(20f)
    b.setPadding(16.dp(), 0, 16.dp(), 0)
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp())
    lp.setMargins(16.dp(), 8.dp(), 16.dp(), 12.dp())
    b.layoutParams = lp
    b.setOnClickListener { onClick() }
    return b
}
/** Gem 次要按钮：高48dp、圆角20dp */
fun ghostBtn(ctx: Context, text: String, onClick: () -> Unit): TextView {
    val b = TextView(ctx)
    b.text = text
    b.setTextColor(C.TXT)
    b.textSize = 16f
    b.gravity = Gravity.CENTER
    b.background = solid(C.CARD2, 20f)
    b.setPadding(14.dp(), 0, 14.dp(), 0)
    val lp = LinearLayout.LayoutParams(0, 48.dp(), 1f)
    lp.setMargins(0, 8.dp(), 0, 12.dp())
    b.layoutParams = lp
    b.setOnClickListener { onClick() }
    return b
}

/** 输入字段：label + 输入框，返回 holder（.input 为 EditText） */
class Field(val root: LinearLayout, val input: EditText)
fun field(ctx: Context, label: String, hint: String, numeric: Boolean = false, multiline: Boolean = false): Field {
    val root = LinearLayout(ctx)
    root.orientation = LinearLayout.VERTICAL
    root.setPadding(16.dp(), 6.dp(), 16.dp(), 6.dp())
    val l = tv(ctx, label, 12f, C.SUB)
    l.setPadding(0, 4.dp(), 0, 6.dp())
    root.addView(l)
    val e = EditText(ctx)
    e.hint = hint
    e.setHintTextColor(C.SUB)
    e.setTextColor(C.TXT)
    e.textSize = 15f
    e.background = solid(C.CARD2, 14f, 1, C.LINE)
    e.setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
    if (numeric) e.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
    if (multiline) {
        e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        e.minLines = 2
    }
    e.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    root.addView(e)
    return Field(root, e)
}

/** 二维码图片（白底圆角），content 为空显示占位 */
fun qrImage(ctx: Context, content: String, sizeDp: Int): ImageView {
    val wrap = ImageView(ctx)
    wrap.setBackgroundColor(Color.WHITE)
    val pad = 12.dp()
    wrap.setPadding(pad, pad, pad, pad)
    val s = sizeDp.dp()
    wrap.layoutParams = LinearLayout.LayoutParams(s, s)
    if (content.isNotBlank()) {
        Thread {
            val bmp = QrCode.toBitmap(content, 8, 4)
            wrap.post { wrap.setImageBitmap(bmp) }
        }.start()
    }
    return wrap
}

/** 设置行图标圆底 */
fun setIc(ctx: Context, res: Int, bg: Int, fg: Int): LinearLayout {
    val box = LinearLayout(ctx)
    box.gravity = Gravity.CENTER
    box.background = solid(bg, 12f)
    val lp = LinearLayout.LayoutParams(40.dp(), 40.dp())
    box.layoutParams = lp
    box.addView(icon(ctx, res, 22, fg))
    return box
}

/** 设置行 */
fun setItem(ctx: Context, ic: View, name: String, value: String, sub: String = "", onClick: (() -> Unit)? = null): LinearLayout {
    val item = LinearLayout(ctx)
    item.gravity = Gravity.CENTER_VERTICAL
    item.setPadding(16.dp(), 13.dp(), 16.dp(), 13.dp())
    item.addView(ic)
    val mid = LinearLayout(ctx)
    mid.orientation = LinearLayout.VERTICAL
    val mlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    mlp.marginStart = 12.dp()
    mid.layoutParams = mlp
    val n = tv(ctx, name, 15f, C.TXT)
    mid.addView(n)
    if (sub.isNotBlank()) {
        val s = tv(ctx, sub, 11f, C.SUB)
        mid.addView(s)
    }
    item.addView(mid)
    if (value.isNotBlank()) {
        val v = tv(ctx, value, 13f, C.SUB)
        v.setPadding(0, 0, 6.dp(), 0)
        item.addView(v)
    }
    if (onClick != null) {
        item.addView(tv(ctx, "›", 20f, C.SUB))
        item.setOnClickListener { onClick.invoke() }
    }
    return item
}
fun divider(ctx: Context): View {
    val v = View(ctx)
    v.setBackgroundColor(C.LINE)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    return v
}
/** Gem 风格分组卡片（大圆角） */
fun setGroup(ctx: Context, items: List<View>): LinearLayout {
    val card = LinearLayout(ctx)
    card.orientation = LinearLayout.VERTICAL
    card.background = solid(C.CARD, 20f)
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.setMargins(16.dp(), 0, 16.dp(), 14.dp())
    card.layoutParams = lp
    items.forEachIndexed { i, it ->
        card.addView(it)
        if (i < items.size - 1) card.addView(divider(ctx))
    }
    return card
}
