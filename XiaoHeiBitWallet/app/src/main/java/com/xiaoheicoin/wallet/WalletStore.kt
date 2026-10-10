package com.xiaoheicoin.wallet

import android.content.Context
import org.json.JSONObject

/**
 * 钱包本地状态管理：列表存 APP 私有目录，余额从服务端查。
 */
object WalletStore {
    @Volatile var list: List<LocalWallet.Wallet> = emptyList()
    @Volatile var curIdx: Int = -1

    fun init(ctx: Context) {
        list = LocalWallet.loadAll(ctx)
        curIdx = LocalWallet.currentIndex(ctx).coerceIn(-1, (list.size - 1).coerceAtLeast(-1))
        LogUtil.d("WalletStore", "init: ${list.size} wallets, currentIdx=$curIdx")
        list.forEachIndexed { i, w -> LogUtil.d("WalletStore", "  [$i] name=${w.name} addr=${w.address.take(16)}... chain=${w.importedChain ?: "multi"}") }
    }

    fun current(): LocalWallet.Wallet? =
        if (curIdx in list.indices) list[curIdx] else null

    private fun persist(ctx: Context) {
        LocalWallet.saveAll(ctx, list)
        LocalWallet.setCurrent(ctx, curIdx)
    }

    fun createRandom(ctx: Context): LocalWallet.Wallet {
        val w = LocalWallet.createRandom()
        list = list + w
        curIdx = list.size - 1
        persist(ctx)
        return w
    }

    /** 多币种：BIP39 助记词创建 */
    fun createWithMnemonic(ctx: Context): LocalWallet.Wallet {
        val mn = LocalWallet.generateMnemonic()
        val w = LocalWallet.importMnemonic(mn, "多币种钱包")
        list = list + w
        curIdx = list.size - 1
        persist(ctx)
        return w
    }

    fun importKey(ctx: Context, key: String, chain: String? = null): LocalWallet.Wallet {
        val w = LocalWallet.importPriv(key, chain = chain)
        list = list + w
        curIdx = list.size - 1
        persist(ctx)
        return w
    }

    fun importMnemonic(ctx: Context, m: String): LocalWallet.Wallet {
        val w = LocalWallet.importMnemonic(m)
        list = list + w
        curIdx = list.size - 1
        persist(ctx)
        return w
    }

    fun switchTo(ctx: Context, idx: Int) {
        LogUtil.d("WalletStore", "switchTo: idx=$idx, oldIdx=$curIdx, total=${list.size}")
        curIdx = idx
        persist(ctx)
        LogUtil.d("WalletStore", "switched to: ${list.getOrNull(idx)?.name} addr=${list.getOrNull(idx)?.address?.take(16)}...")
    }

    fun rename(ctx: Context, idx: Int, name: String, icon: String = "") {
        if (idx !in list.indices) return
        val old = list[idx]
        list = list.toMutableList().also { it[idx] = old.copy(name = name, icon = icon.ifBlank { old.icon }) }
        persist(ctx)
    }

    /** 给助记词钱包添加新地址（派生下一个index），返回新钱包在list中的索引 */
    fun addAddress(ctx: Context, walletIdx: Int): Int {
        val w = list.getOrNull(walletIdx) ?: return -1
        val mn = w.mnemonic ?: return -1
        // 找到同一助记词的最大 addrIndex
        val maxIndex = list.filter { it.mnemonic == mn }.maxOfOrNull { it.addrIndex } ?: 0
        val newIndex = maxIndex + 1
        val newWallet = LocalWallet.deriveFromMnemonic(mn, newIndex, "${w.name} #${newIndex + 1}")
        list = list + newWallet
        persist(ctx)
        return list.size - 1
    }

    fun delete(ctx: Context, idx: Int) {
        list = list.toMutableList().also { it.removeAt(idx) }
        if (curIdx >= list.size) curIdx = list.size - 1
        if (list.isEmpty()) curIdx = -1
        persist(ctx)
    }

    /** 本地签名并发送转账 */
    fun send(ctx: Context, to: String, amount: Double, memo: String = ""): JSONObject {
        val w = current() ?: throw RuntimeException("没有钱包")
        val ts = System.currentTimeMillis() / 1000
        val nonce = (System.currentTimeMillis() % (1L shl 31)).toInt()
        val amtInt = amount.toInt()
        var msg = w.address + "|" + to + "|" + amtInt + "|" + nonce + "|" + ts
        if (memo.isNotBlank()) msg += "|$memo"
        val sig = LocalWallet.sign(w.privateKeyHex, msg)
        return Api.post("/api/transfer_signed", JSONObject()
            .put("sender", w.address)
            .put("public_key", w.publicKeyHex)
            .put("receiver", to)
            .put("amount", amtInt)
            .put("nonce", nonce)
            .put("timestamp", ts)
            .put("signature", sig)
            .put("memo", memo))
    }
}
