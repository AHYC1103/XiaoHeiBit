package com.xiaoheibit.wallets

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 第一次启动引导页：红底+国旗+三个确认+进入按钮
 */
class OnboardingActivity : Activity() {

    private val checks = BooleanArray(3)
    private lateinit var btnEnter: TextView
    private lateinit var checkViews: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Api.load(this)
        window.statusBarColor = Color.parseColor("#de2910")
        window.navigationBarColor = Color.parseColor("#de2910")

        val sp = getSharedPreferences("onboard", MODE_PRIVATE)
        if (sp.getBoolean("done", false)) {
            goMain(); return
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#de2910"))
        root.gravity = Gravity.CENTER_HORIZONTAL

        // 国旗
        val flag = ImageView(this)
        flag.setImageResource(R.drawable.ic_flag)
        flag.scaleType = ImageView.ScaleType.FIT_CENTER
        flag.setPadding(3.dp(), 3.dp(), 3.dp(), 3.dp())
        flag.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(3.dp(), Color.BLACK)
        }
        val lp = LinearLayout.LayoutParams(220.dp(), 145.dp())
        lp.topMargin = 100.dp()
        flag.layoutParams = lp
        root.addView(flag)

        // 三个确认项
        val texts = listOf(
            "1. 小黑币钱包，不保证虚拟货币财产安全",
            "2. 在小黑币钱包内，如有财产损失，作者概不负责",
            "3. 在中华人民共和国，虚拟货币不受法律保护"
        )
        checkViews = texts.mapIndexed { i, t ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(20.dp(), 10.dp(), 20.dp(), 10.dp())

            val circle = TextView(this)
            circle.text = "○"
            circle.textSize = 20f
            circle.setTextColor(Color.WHITE)
            circle.layoutParams = LinearLayout.LayoutParams(40.dp(), -2)
            row.addView(circle)

            val label = TextView(this)
            label.text = t
            label.setTextColor(Color.WHITE)
            label.textSize = 14f
            label.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            row.addView(label)

            row.setOnClickListener {
                checks[i] = !checks[i]
                circle.text = if (checks[i]) "●" else "○"
                circle.setTextColor(Color.parseColor("#1e6fff"))
                updateBtn()
            }
            root.addView(row)
            circle
        }

        // 弹簧
        val spacer = View(this)
        val sp2 = LinearLayout.LayoutParams(-1, 0, 1f)
        spacer.layoutParams = sp2
        root.addView(spacer)

        // 进入按钮
        btnEnter = TextView(this)
        btnEnter.text = "我已知晓！！！"
        btnEnter.setTextColor(Color.GRAY)
        btnEnter.textSize = 18f
        btnEnter.gravity = Gravity.CENTER
        btnEnter.setPadding(20.dp(), 14.dp(), 20.dp(), 14.dp())
        btnEnter.setBackgroundColor(Color.parseColor("#555555"))
        btnEnter.isEnabled = false
        val bp = LinearLayout.LayoutParams(280.dp(), 50.dp())
        bp.bottomMargin = 60.dp()
        btnEnter.layoutParams = bp
        btnEnter.setOnClickListener {
            sp.edit().putBoolean("done", true).apply()
            goMain()
        }
        root.addView(btnEnter)

        setContentView(root)
    }

    private fun updateBtn() {
        val all = checks.all { it }
        btnEnter.isEnabled = all
        btnEnter.setTextColor(if (all) Color.BLACK else Color.GRAY)
        btnEnter.setBackgroundColor(if (all) Color.parseColor("#1e6fff") else Color.parseColor("#555555"))
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
