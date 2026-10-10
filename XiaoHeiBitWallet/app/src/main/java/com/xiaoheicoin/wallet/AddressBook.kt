package com.xiaoheicoin.wallet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 地址簿：保存常用收款地址，支持置顶/修改/删除 */
object AddressBook {
    private const val PREFS = "xhb_address_book"
    private const val KEY_LIST = "addresses"

    data class Addr(val label: String, val address: String, val chain: String)

    fun list(ctx: Context): List<Addr> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val result = mutableListOf<Addr>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(Addr(obj.optString("label"), obj.optString("address"), obj.optString("chain")))
        }
        return result
    }

    fun add(ctx: Context, label: String, address: String, chain: String) {
        val list = list(ctx).toMutableList()
        list.add(Addr(label, address, chain))
        save(ctx, list)
    }

    fun remove(ctx: Context, address: String) {
        val list = list(ctx).filter { it.address != address }
        save(ctx, list)
    }

    fun update(ctx: Context, oldAddress: String, newLabel: String, newAddress: String, newChain: String) {
        val list = list(ctx).toMutableList()
        val idx = list.indexOfFirst { it.address == oldAddress }
        if (idx >= 0) {
            list[idx] = Addr(newLabel, newAddress, newChain)
            save(ctx, list)
        }
    }

    fun moveToTop(ctx: Context, address: String) {
        val list = list(ctx).toMutableList()
        val idx = list.indexOfFirst { it.address == address }
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(0, item)
            save(ctx, list)
        }
    }

    private fun save(ctx: Context, list: List<Addr>) {
        val arr = JSONArray()
        for (a in list) {
            arr.put(JSONObject().put("label", a.label).put("address", a.address).put("chain", a.chain))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, arr.toString()).apply()
    }
}
