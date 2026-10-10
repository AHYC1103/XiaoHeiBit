package com.xiaoheicoin.wallet

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** 4位数字密码锁 — Telegram风格数字键盘 */
object PasswordLock {
    private const val PREFS = "xhb_security"
    private const val KEY_PIN = "pin_hash"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN, null) != null

    fun setPin(ctx: Context, pin: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PIN, pin.hashCode().toString()).apply()
    }

    fun verify(ctx: Context, pin: String): Boolean {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PIN, null)
        return saved != null && saved == pin.hashCode().toString()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PIN).apply()
    }

    // ========== Telegram风格键盘 ==========

    private fun circleBg(fill: Int, strokeW: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(strokeW, stroke)
    }

    private fun numBtn(ctx: Context, num: String, sub: String, onClick: () -> Unit): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 96.dp(), 1f).apply {
                setMargins(4.dp(), 4.dp(), 4.dp(), 4.dp())
            }
        }
        val btn = TextView(ctx).apply {
            text = num; textSize = 26f; setTextColor(C.TXT); gravity = Gravity.CENTER
            background = circleBg(0x22FFFFFF, 2.dp(), 0x55FFFFFF.toInt())
            setOnClickListener { onClick() }
        }
        btn.layoutParams = LinearLayout.LayoutParams(68.dp(), 68.dp()).apply { gravity = Gravity.CENTER }
        wrap.addView(btn)
        wrap.addView(TextView(ctx).apply {
            text = sub; textSize = 10f; setTextColor(C.SUB); gravity = Gravity.CENTER
            setPadding(0, 3.dp(), 0, 0)
        })
        return wrap
    }

    private fun actionBtn(ctx: Context, symbol: String, onClick: () -> Unit): LinearLayout {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 96.dp(), 1f).apply {
                setMargins(4.dp(), 4.dp(), 4.dp(), 4.dp())
            }
        }
        wrap.addView(TextView(ctx).apply {
            text = symbol
            textSize = if (symbol.length > 1) 14f else 22f
            setTextColor(C.SUB); gravity = Gravity.CENTER
            background = circleBg(0x11FFFFFF, 2.dp(), 0x33FFFFFF.toInt())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(68.dp(), 68.dp()).apply { gravity = Gravity.CENTER }
        })
        wrap.addView(TextView(ctx).apply {
            text = ""; textSize = 10f; setPadding(0, 3.dp(), 0, 0)
        })
        return wrap
    }

    /** 密码键盘Dialog，输入满4位自动回调 */
    private fun pinDialog(
        ctx: Context,
        initialTitle: String,
        onCancel: (() -> Unit)? = null,
        onComplete: (String, PinDialog) -> Unit
    ): PinDialog {
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        var input = ""

        val titleTv = TextView(ctx).apply {
            text = initialTitle; textSize = 20f; setTextColor(C.TXT); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 4.dp())
        }

        val dotsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 20.dp(), 0, 20.dp())
        }
        val dotViews = Array(4) {
            View(ctx).apply {
                background = circleBg(0, 2.dp(), 0x55FFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(14.dp(), 14.dp()).apply {
                    setMargins(10.dp(), 0, 10.dp(), 0)
                }
            }
        }
        dotViews.forEach { dotsRow.addView(it) }

        fun refreshDots() {
            dotViews.forEachIndexed { i, v ->
                v.background = circleBg(
                    if (i < input.length) C.TXT else 0,
                    2.dp(),
                    if (i < input.length) C.TXT else 0x55FFFFFF.toInt()
                )
            }
        }

        val keyboard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12.dp(), 0, 0) }
        }

        fun addDigit(d: String) {
            if (input.length < 4) {
                input += d
                refreshDots()
                if (input.length == 4) {
                    val pin = input
                    onComplete(pin, PinDialog(dialog) {
                        input = ""; refreshDots()
                    })
                }
            }
        }

        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("" to "", "0" to "", "DEL" to "")
        )
        for ((ri, row) in rows.withIndex()) {
            val rl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for ((num, sub) in row) {
                when {
                    ri == 3 && num == "" -> rl.addView(actionBtn(ctx, "✕") {
                        if (onCancel != null) onCancel.invoke() else dialog.dismiss()
                    })
                    ri == 3 && num == "0" -> rl.addView(numBtn(ctx, "0", "") { addDigit("0") })
                    ri == 3 && num == "DEL" -> rl.addView(actionBtn(ctx, "⌫") {
                        if (input.isNotEmpty()) { input = input.dropLast(1); refreshDots() }
                    })
                    else -> rl.addView(numBtn(ctx, num, sub) { addDigit(num) })
                }
            }
            keyboard.addView(rl)
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C.BG)
            setPadding(36.dp(), 60.dp(), 36.dp(), 40.dp())
            gravity = Gravity.CENTER
            addView(titleTv); addView(dotsRow); addView(keyboard)

            // 左滑返回
            var startX = 0f
            var tracking = false
            setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        if (event.x < 30.dp()) { startX = event.x; tracking = true }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (tracking && event.x - startX > 80.dp()) {
                            tracking = false
                            if (onCancel != null) onCancel.invoke() else dialog.dismiss()
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> { tracking = false }
                }
                true
            }
        }

        dialog.setContentView(root)
        dialog.setCancelable(false)
        dialog.show()

        return PinDialog(dialog) { input = ""; refreshDots() }.also { it.titleTv = titleTv }
    }

    class PinDialog(val dialog: Dialog, val reset: () -> Unit) {
        var titleTv: TextView? = null
        fun setTitle(t: String) { titleTv?.text = t }
        fun dismiss() { dialog.dismiss() }
    }

    // ========== 对外接口 ==========

    fun requestVerify(ctx: Context, title: String = "请输入密码", onCancel: (() -> Unit)? = null, onSuccess: () -> Unit) {
        pinDialog(ctx, title, onCancel) { pin, pd ->
            if (verify(ctx, pin)) { pd.dismiss(); onSuccess() }
            else { Nav.toast("密码错误"); pd.reset() }
        }
    }

    fun requestSet(ctx: Context, onDone: () -> Unit = {}) {
        var step = 1; var first = ""
        pinDialog(ctx, "设置密码") { pin, pd ->
            when {
                step == 1 -> { first = pin; step = 2; pd.setTitle("再次输入"); pd.reset() }
                pin == first -> { setPin(ctx, pin); Nav.toast("密码设置成功"); pd.dismiss(); onDone() }
                else -> { Nav.toast("两次输入不一致"); step = 1; first = ""; pd.setTitle("设置密码"); pd.reset() }
            }
        }
    }

    fun requestClear(ctx: Context, onDone: () -> Unit = {}) {
        pinDialog(ctx, "输入当前密码以关闭") { pin, pd ->
            if (verify(ctx, pin)) { clear(ctx); Nav.toast("密码已关闭"); pd.dismiss(); onDone() }
            else { Nav.toast("密码错误"); pd.reset() }
        }
    }
}
