package com.xiaoheicoin.wallet

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            onCreateSafe()
        } catch (e: Throwable) {
            val tv = android.widget.TextView(this)
            tv.text = android.util.Log.getStackTraceString(e)
            tv.textSize = 10f; tv.setTextColor(0xFFFF0000.toInt()); tv.setPadding(20, 20, 20, 20)
            val sv = android.widget.ScrollView(this); sv.addView(tv)
            setContentView(sv)
        }
    }

    private fun onCreateSafe() {
        LogUtil.init(this)
        LogUtil.i("MainActivity", "=== APP 启动 ===")
        LogUtil.i("MainActivity", "版本: ${packageManager.getPackageInfo(packageName, 0).versionName} (code=${packageManager.getPackageInfo(packageName, 0).versionCode})")
        LogUtil.i("MainActivity", "设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} SDK=${android.os.Build.VERSION.SDK_INT}")
        LogUtil.i("MainActivity", "ChaCha20自测: ${WCCrypto.selfTest()}")
        LogUtil.i("MainActivity", "请求存储权限")
        WalletStore.init(this)
        LogUtil.i("MainActivity", "WalletStore 初始化完成")
        Api.load(this)
        LogUtil.i("MainActivity", "Api 加载完成")
        RemoteConfig.load(this)
        LogUtil.i("MainActivity", "RemoteConfig 加载完成")
        window.statusBarColor = C.BG
        window.decorView.systemUiVisibility = 0
        window.navigationBarColor = C.TABBAR

        val root = FrameLayout(this)
        root.setBackgroundColor(C.BG)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        val tabbar = buildTabbar()
        col.addView(content); col.addView(tabbar)
        root.addView(col)
        overlay = FrameLayout(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        Nav.init(this, content, tabbar, overlay)

        Nav.register("wallet", WalletPages.buildWallet()) { WalletPages.bindWallet() }
        Nav.register("coin", WalletPages.buildCoin()) { WalletPages.bindCoin() }
        Nav.register("txs", WalletPages.buildTxs()) { WalletPages.bindTxs() }
        Nav.register("buy", WalletPages.buildBuy()) { WalletPages.bindBuy() }
        Nav.register("more", SettingsPages.buildMore()) {}
        Nav.register("set", SettingsPages.buildSet()) { SettingsPages.bindSet() }
        Nav.register("nft", NftPages.buildList()) { NftPages.bindList() }
        Nav.register("nftDetail", NftPages.buildDetail()) { NftPages.bindDetail() }
        Nav.register("nftMint", NftPages.buildMint()) {}
        Nav.register("nftSend", NftPages.buildSend()) {}
        Nav.register("nftRecv", NftPages.buildRecv()) { NftPages.bindRecv() }
        Nav.register("swap", SwapPages.build()) {}
        Nav.register("dapp", DAppPages.build()) {}
        Nav.register("wc", WCPages.build()) {}

        MainApp.onRefresh = { doRefresh() }
        if (PasswordLock.isEnabled(this)) {
            PasswordLock.requestVerify(this, "请输入密码解锁", onCancel = { finish() }) {
                Nav.go("wallet")
                doRefresh()
            }
        } else {
            Nav.go("wallet")
            doRefresh()
        }
        handler.postDelayed(object : Runnable {
            override fun run() { doRefresh(); handler.postDelayed(this, 30000) }
        }, 5000)
    }

    private fun doRefresh() {
        LogUtil.d("MainActivity", "doRefresh start, current wallet=${WalletStore.current()?.address?.take(12)}...")
        State.current = WalletStore.current()?.address
        runCatching { bindCurrent() }
        Nav.io {
            val t0 = System.currentTimeMillis()
            State.refresh()
            LogUtil.d("MainActivity", "State.refresh done in ${System.currentTimeMillis()-t0}ms")
            Nav.ui { runCatching { bindCurrent() } }
        }
    }

    private fun bindCurrent() {
        LogUtil.d("MainActivity", "bindCurrent page=${Nav.cur}")
        when (Nav.cur) {
            "wallet" -> WalletPages.bindWallet()
            "coin" -> { WalletPages.bindWallet(); WalletPages.bindCoin() }
            "txs" -> { WalletPages.bindWallet(); WalletPages.bindTxs() }
            "buy" -> { WalletPages.bindWallet(); WalletPages.bindBuy() }
            "set" -> { WalletPages.bindWallet(); SettingsPages.bindSet() }
            "nft" -> { WalletPages.bindWallet(); NftPages.bindList() }
            "nftDetail" -> NftPages.bindDetail()
        }
    }

    private fun buildTabbar(): LinearLayout {
        val wrap = LinearLayout(this); wrap.orientation = LinearLayout.VERTICAL
        wrap.setBackgroundColor(C.TABBAR)
        val line = View(this); line.setBackgroundColor(C.LINE)
        wrap.addView(line, LinearLayout.LayoutParams(-1, 1))
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 6.dp(), 0, 8.dp())
        val icons = ArrayList<ImageView>(); val labels = ArrayList<TextView>()
        listOf(
            Triple(R.drawable.ic_wallet, "钱包", "wallet"),
            Triple(R.drawable.ic_history, "更多", "more"),
            Triple(R.drawable.ic_settings, "设置", "set")
        ).forEach { (res, name, key) ->
            val item = LinearLayout(this); item.orientation = LinearLayout.VERTICAL; item.gravity = Gravity.CENTER
            item.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            val iv = ImageView(this); iv.setImageResource(res)
            iv.layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
            val lab = TextView(this); lab.text = name; lab.textSize = 11f; lab.gravity = Gravity.CENTER
            lab.setPadding(0, 3.dp(), 0, 0)
            item.addView(iv); item.addView(lab)
            item.setOnClickListener { Nav.go(key) }
            row.addView(item); icons.add(iv); labels.add(lab)
        }
        wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
        Nav.tabIcons = icons; Nav.tabLabels = labels
        return wrap
    }

    private fun requestStoragePermission() {
        try {
            val perms = arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            requestPermissions(perms, 1001)
            LogUtil.i("MainActivity", "请求存储权限")
        } catch (e: Exception) {
            LogUtil.e("MainActivity", "请求权限失败: ${e.message}")
        }
    }

    override fun onBackPressed() {
        if (!Nav.back()) {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        LogUtil.i("MainActivity", "权限结果: requestCode=$requestCode results=${grantResults.joinToString()}")
    }
}
