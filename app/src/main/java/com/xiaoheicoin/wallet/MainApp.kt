package com.xiaoheicoin.wallet

import android.content.Intent

/** 全局刷新回调 + 扫码结果分发（对应 webui startScan 的码类型路由） */
object MainApp {
    var onRefresh: () -> Unit = {}
    fun refreshAll() = onRefresh()

    fun handleScan(code: String) {
        val p = code.split("$")
        if (p.size >= 3 && p[0] == "XiaoHeiCoin") {
            when (p[1]) {
                "CollectMoney" -> { WS.send(); Nav.toast("已识别收款地址") }
                "leading-In" -> Nav.io {
                    try { WalletStore.importKey(Nav.act, p[2]); State.refresh(); Nav.ui { Nav.toast("钱包导入成功"); refreshAll() } }
                    catch (e: Exception) { Nav.ui { Nav.toast(e.message ?: "失败") } }
                }
                "Nft" -> { NftPages.prefillRecv(code); Nav.go("nftRecv") }
                "DApp" -> Nav.toast("DApp 开发中")
                else -> Nav.toast("未知二维码格式")
            }
        } else {
            WS.send(); Nav.toast("已识别")
        }
    }
}

object ScanUtil {
    var cb: ((String) -> Unit)? = null
    fun open(cb: (String) -> Unit) {
        this.cb = cb
        val i = Intent(Nav.act, ScanActivity::class.java)
        Nav.act.startActivity(i)
    }
}
