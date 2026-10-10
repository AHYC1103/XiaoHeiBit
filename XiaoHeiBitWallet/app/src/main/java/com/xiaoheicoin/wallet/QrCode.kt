package com.xiaoheicoin.wallet

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 零依赖 QR Code 生成器，对齐 webui 内置 qrcode-generator：typeNumber 自动、EC=M、Byte/UTF-8。
 * 覆盖 version 1-6（单段 alignment，块长均一），足够承载地址码/NFT码/私钥码。
 * 输出 true=黑模块的方阵，再由 [toBitmap] 渲染（含 4 模块静区）。
 */
object QrCode {

    // EC=M：每版本 [每块EC码字数, 块数, 每块数据码字数]（v1..v6，块长均一）
    private val M_TABLE = arrayOf(
        intArrayOf(10, 1, 16),  // v1 total26
        intArrayOf(16, 1, 28),  // v2 total44
        intArrayOf(26, 1, 44),  // v3 total70
        intArrayOf(18, 2, 32),  // v4 total100
        intArrayOf(24, 2, 43),  // v5 total134
        intArrayOf(16, 4, 27)   // v6 total172
    )
    private val ALIGN_CENTER = intArrayOf(-1, -1, 18, 22, 26, 30, 34)

    // GF(256), primitive poly 0x11d
    private val EXP = IntArray(256)
    private val LOG = IntArray(256)
    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11d
        }
        EXP[255] = EXP[0]
    }
    private fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP[(LOG[a] + LOG[b]) % 255]

    private fun rsDivisor(deg: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until deg) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], EXP[i])
            }
            poly = next
        }
        return poly
    }
    /** 对数据块做 RS 多项式长除法，返回 ecCount 个纠错码字（高次在前，与生成多项式一致） */
    private fun ecc(data: IntArray, ec: Int): IntArray {
        val gen = rsDivisor(ec)
        val poly = IntArray(data.size + ec)
        for (i in data.indices) poly[i] = data[i]
        for (i in data.indices) {
            val coef = poly[i]
            if (coef != 0) for (j in 0..ec) poly[i + j] = poly[i + j] xor gfMul(gen[j], coef)
        }
        return poly.copyOfRange(data.size, poly.size)
    }

    private class BitBuffer {
        val bits = ArrayList<Int>()
        fun put(v: Int, len: Int) { for (i in len - 1 downTo 0) bits.add((v shr i) and 1) }
    }

    fun encode(content: String): Array<BooleanArray> {
        val bytes = content.toByteArray(Charsets.UTF_8)
        // 选最小版本
        var vi = 0
        for (i in M_TABLE.indices) {
            val dataLen = M_TABLE[i][1] * M_TABLE[i][2]
            val ccBits = if (i + 1 <= 9) 8 else 16
            if (4 + ccBits + bytes.size * 8 <= dataLen * 8) { vi = i; break }
            if (i == M_TABLE.lastIndex) vi = i
        }
        val version = vi + 1
        val n = 4 * version + 17
        val (ecPer, blockCount, dataPer) = M_TABLE[vi].let { Triple(it[0], it[1], it[2]) }

        // 组装数据位
        val bb = BitBuffer()
        bb.put(0b0100, 4)                                   // Byte 模式
        bb.put(bytes.size, if (version <= 9) 8 else 16)    // 字符数
        bytes.forEach { bb.put(it.toInt() and 0xFF, 8) }
        val totalData = blockCount * dataPer
        var cap = totalData * 8
        repeat(minOf(4, cap - bb.bits.size)) { bb.bits.add(0) }  // 终止符
        while (bb.bits.size % 8 != 0) bb.bits.add(0)
        var pad = 0xEC
        while (bb.bits.size < cap) { bb.put(pad, 8); pad = if (pad == 0xEC) 0x11 else 0xEC }

        // 码字 + 分块 + RS
        val codewords = IntArray(totalData) { (bb.bits[it * 8] shl 7) or (bb.bits[it * 8 + 1] shl 6) or
            (bb.bits[it * 8 + 2] shl 5) or (bb.bits[it * 8 + 3] shl 4) or (bb.bits[it * 8 + 4] shl 3) or
            (bb.bits[it * 8 + 5] shl 2) or (bb.bits[it * 8 + 6] shl 1) or bb.bits[it * 8 + 7] }
        val blocks = (0 until blockCount).map { b ->
            val d = codewords.copyOfRange(b * dataPer, (b + 1) * dataPer)
            d to ecc(d, ecPer)
        }
        val final = ArrayList<Int>()
        for (i in 0 until dataPer) blocks.forEach { final.add(it.first[i]) }
        for (i in 0 until ecPer) blocks.forEach { final.add(it.second[i]) }
        val dataBits = BooleanArray(final.size * 8) { k -> ((final[k / 8] shr (7 - k % 8)) and 1) == 1 }

        // 矩阵
        val modules = Array(n) { BooleanArray(n) }
        val reserved = Array(n) { BooleanArray(n) }
        fun set(r: Int, c: Int, dark: Boolean, isFunc: Boolean) {
            if (r !in 0 until n || c !in 0 until n) return
            modules[r][c] = dark; reserved[r][c] = isFunc
        }
        fun finder(r0: Int, c0: Int) {
            for (dr in -1..7) for (dc in -1..7) {
                val r = r0 + dr; val c = c0 + dc
                if (r !in 0 until n || c !in 0 until n) continue
                val dark = dr in 0..6 && dc in 0..6 &&
                    (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                set(r, c, dark, true)
            }
        }
        finder(0, 0); finder(0, n - 7); finder(n - 7, 0)
        // timing
        for (i in 0 until n) {
            if (!reserved[6][i]) set(6, i, i % 2 == 0, true)
            if (!reserved[i][6]) set(i, 6, i % 2 == 0, true)
        }
        // alignment
        if (version >= 2) {
            val p = ALIGN_CENTER[version]
            for (dr in -2..2) for (dc in -2..2) {
                val r = p + dr; val c = p + dc
                if (r !in 0 until n || c !in 0 until n) continue
                val dark = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                set(r, c, dark, true)
            }
        }
        // 预留 format 区
        for (i in 0..8) { set(8, i, false, true); set(i, 8, false, true) }
        for (i in 0 until 8) set(8, n - 1 - i, false, true)
        for (i in 0 until 7) set(n - 1 - i, 8, false, true)
        set(n - 8, 8, true, true) // 固定黑模块

        // 数据列（排除 timing 列 6），从右向左成对
        val cols = (0 until n).filter { it != 6 }.reversed()
        val pairs = cols.chunked(2)
        var bitIdx = 0
        pairs.forEachIndexed { pi, pair ->
            val upward = pi % 2 == 0
            for (vert in 0 until n) {
                val r = if (upward) n - 1 - vert else vert
                pair.forEach { c ->
                    if (!reserved[r][c] && bitIdx < dataBits.size) {
                        var dark = dataBits[bitIdx++]
                        if ((r + c) % 2 == 0) dark = !dark   // mask pattern 0
                        set(r, c, dark, false)
                    }
                }
            }
        }
        // format bits：EC=M(00) + mask0(000)
        var fmt = 0
        var rem = fmt shl 10
        for (i in 14 downTo 10) if ((rem shr i) and 1 == 1) rem = rem xor (0x537 shl (i - 10))
        val format = ((fmt shl 10) or (rem and 0x3FF)) xor 0x5412
        fun fb(i: Int) = (format shr i) and 1 == 1
        // 第一份：col8 垂直段 + row8 水平段
        for (i in 0..5) set(i, 8, fb(i), true)
        set(7, 8, fb(6), true); set(8, 8, fb(7), true); set(8, 7, fb(8), true)
        for (i in 9..14) set(8, 14 - i, fb(i), true)
        // 第二份：row8 右侧水平 8 位 + col8 底部垂直 7 位
        for (i in 0..7) set(8, n - 1 - i, fb(i), true)
        for (i in 8..14) set(n - 15 + i, 8, fb(i), true)

        return modules
    }

    fun toBitmap(content: String, modulePx: Int = 8, quiet: Int = 4): Bitmap {
        val m = encode(content)
        val cnt = m.size
        val total = cnt + quiet * 2
        val bmp = Bitmap.createBitmap(total * modulePx, total * modulePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (r in 0 until cnt) for (c in 0 until cnt) {
            if (m[r][c]) {
                val x = (c + quiet) * modulePx; val y = (r + quiet) * modulePx
                for (dy in 0 until modulePx) for (dx in 0 until modulePx) bmp.setPixel(x + dx, y + dy, Color.BLACK)
            }
        }
        return bmp
    }
}
