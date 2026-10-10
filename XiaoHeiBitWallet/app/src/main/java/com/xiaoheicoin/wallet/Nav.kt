package com.xiaoheicoin.wallet

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

/** 页面导航 + 底部弹窗 + 通用异步/图片工具，对应 webui 的 page 切换与 mask/sheet */
object Nav {
    lateinit var act: Activity
    lateinit var content: FrameLayout
    lateinit var tabbar: LinearLayout
    private lateinit var overlay: FrameLayout
    private val sheetStack = ArrayDeque<View>()

    private val pages = LinkedHashMap<String, View>()
    private val onShow = HashMap<String, () -> Unit>()
    private val history = ArrayDeque<String>()
    private val tabRoots = setOf("wallet", "more", "set")
    var cur = "wallet"

    // 三个 tab 的图标/文字（用于高亮）
    var tabIcons = listOf<ImageView>()
    var tabLabels = listOf<TextView>()

    private val tabGroup = mapOf(
        "wallet" to 0, "coin" to 0, "txs" to 0, "buy" to 0,
        "more" to 1, "nft" to 1, "nftSend" to 1, "nftRecv" to 1, "nftDetail" to 1, "nftMint" to 1,
        "set" to 2, "dapp" to 2, "swap" to 0
    )

    fun init(a: Activity, c: FrameLayout, tb: LinearLayout, ov: FrameLayout) {
        act = a; content = c; tabbar = tb; overlay = ov
    }
    fun register(key: String, v: View, show: () -> Unit = {}) {
        val wrapped: View = if (key != "set" && v is android.widget.ScrollView) {
            val srl = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(act)
            srl.addView(v, FrameLayout.LayoutParams(-1, -1))
            srl.setOnRefreshListener {
                Nav.io {
                    State.refresh()
                    Nav.ui {
                        runCatching {
                            when (Nav.cur) {
                                "wallet" -> WalletPages.bindWallet()
                                "coin" -> { WalletPages.bindWallet(); WalletPages.bindCoin() }
                                "txs" -> { WalletPages.bindWallet(); WalletPages.bindTxs() }
                                "buy" -> { WalletPages.bindWallet(); WalletPages.bindBuy() }
                                "more" -> { WalletPages.bindWallet() }
                                "nft" -> { WalletPages.bindWallet(); NftPages.bindList() }
                                "nftDetail" -> NftPages.bindDetail()
                                "swap" -> { WalletPages.bindWallet() }
                                else -> {}
                            }
                        }
                        srl.isRefreshing = false
                    }
                }
            }
            srl
        } else v
        wrapped.visibility = View.GONE
        content.addView(wrapped, FrameLayout.LayoutParams(-1, -1))
        pages[key] = wrapped; onShow[key] = show
    }
    fun go(key: String) {
        if (!pages.containsKey(key)) return
        // 跳转到tab根页面时清空历史
        if (key in tabRoots) history.clear()
        else if (cur != key) history.addLast(cur)
        pages.values.forEach { it.visibility = View.GONE }
        pages[key]!!.visibility = View.VISIBLE
        cur = key
        tabbar.visibility = if (key == "buy") View.GONE else View.VISIBLE
        val g = tabGroup[key]
        tabIcons.forEachIndexed { i, iv -> iv.setColorFilter(if (g == i) C.BLUE else C.SUB) }
        tabLabels.forEachIndexed { i, t -> t.setTextColor(if (g == i) C.BLUE else C.SUB) }
        onShow[key]?.invoke()
    }

    /** 返回上一页，没有历史则返回false（调用方退出应用） */
    fun back(): Boolean {
        if (sheetStack.isNotEmpty()) { closeSheet(); return true }
        if (history.isNotEmpty()) {
            val prev = history.removeLast()
            pages.values.forEach { it.visibility = View.GONE }
            pages[prev]!!.visibility = View.VISIBLE
            cur = prev
            tabbar.visibility = if (prev == "buy") View.GONE else View.VISIBLE
            val g = tabGroup[prev]
            tabIcons.forEachIndexed { i, iv -> iv.setColorFilter(if (g == i) C.BLUE else C.SUB) }
            tabLabels.forEachIndexed { i, t -> t.setTextColor(if (g == i) C.BLUE else C.SUB) }
            onShow[prev]?.invoke()
            return true
        }
        return false
    }

    /** 弹出标准底部弹窗（handle + 居中标题 + 可滚动内容） */
    fun sheet(title: String, body: View, cancellable: Boolean = true, onClose: (() -> Unit)? = null) {
        // 已有sheet的遮罩改透明，避免叠加变黑
        sheetStack.forEach { it.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        val mask = FrameLayout(act)
        mask.setBackgroundColor(0xB3000000.toInt())
        mask.setOnClickListener { if (cancellable) { closeSheet(); onClose?.invoke() } }

        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = solid(SHEET_BG(), 28f).apply { setShapeTop() }
        val clp = FrameLayout.LayoutParams(-1, -2)
        clp.gravity = Gravity.BOTTOM
        card.layoutParams = clp
        card.setPadding(20.dp(), 10.dp(), 20.dp(), 24.dp())
        card.setOnClickListener { /* 拦截，不关闭 */ }

        val handle = View(act)
        handle.background = solid(C.LINE, 2f)
        val hlp = LinearLayout.LayoutParams(40.dp(), 4.dp())
        hlp.gravity = Gravity.CENTER_HORIZONTAL
        hlp.bottomMargin = 14.dp()
        handle.layoutParams = hlp
        card.addView(handle)

        if (title.isNotBlank()) {
            val t = tv(act, title, 17f, C.TXT, true)
            t.gravity = Gravity.CENTER
            val tlp = LinearLayout.LayoutParams(-1, -2)
            tlp.bottomMargin = 14.dp()
            t.layoutParams = tlp
            card.addView(t)
        }
        val sv = ScrollView(act)
        val maxH = (act.resources.displayMetrics.heightPixels * 0.82).toInt()
        body.layoutParams = FrameLayout.LayoutParams(-1, -2)
        sv.addView(body)
        val svlp = LinearLayout.LayoutParams(-1, -2)
        svlp.gravity = Gravity.CENTER_HORIZONTAL
        sv.layoutParams = svlp
        sv.viewTreeObserver.addOnGlobalLayoutListener {
            if (sv.getChildAt(0).height > maxH) sv.layoutParams = LinearLayout.LayoutParams(-1, maxH)
        }
        card.addView(sv)

        mask.addView(card)
        overlay.addView(mask)
        card.translationY = 300.dp().toFloat()
        card.animate().translationY(0f).setDuration(180).start()
        sheetStack.addLast(mask)
    }
    fun closeSheet() {
        if (sheetStack.isNotEmpty()) {
            val mask = sheetStack.removeLast()
            overlay.removeView(mask)
            // 关闭后新的最上层恢复黑色遮罩
            sheetStack.lastOrNull()?.setBackgroundColor(0xB3000000.toInt())
        }
    }
    private fun SHEET_BG() = C.SHEET
    private fun GradientDrawable.setShapeTop() {
        // 顶部圆角（cornerRadius 已四角圆角，底部被屏幕遮住，无影响）
    }

    fun toast(m: String) = Toast.makeText(act, m, Toast.LENGTH_SHORT).show()
    fun io(block: () -> Unit) = Thread {
        try { block() } catch (e: Throwable) { ui { runCatching { toast(e.message ?: "出错了") } } }
    }.start()
    fun ui(block: () -> Unit) = act.runOnUiThread { block() }

    fun ipfs(cid: String) = "https://ipfs.thegraph.com/ipfs/${cid.trim()}"

    private val imgCache = HashMap<String, Bitmap>()

    /** 后台加载网络图片到 ImageView（带内存缓存，轮询重复调用不闪烁） */
    fun loadImg(url: String, iv: ImageView, onDone: ((Boolean) -> Unit)? = null) {
        val cached = imgCache[url]
        if (cached != null) { if (iv.drawable == null) iv.setImageBitmap(cached); onDone?.invoke(true); return }
        io {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                c.disconnect()
                if (bmp != null) { imgCache[url] = bmp; ui { iv.setImageBitmap(bmp); onDone?.invoke(true) } }
                else ui { onDone?.invoke(false) }
            } catch (_: Exception) { ui { onDone?.invoke(false) } }
        }
    }
}
