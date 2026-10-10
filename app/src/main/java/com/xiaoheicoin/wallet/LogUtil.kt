package com.xiaoheicoin.wallet

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全局日志工具：同时输出 logcat 和私有目录 xiaohei.txt */
object LogUtil {

    private var logFile: File? = null
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // 日志开关：false=只输出logcat不写文件（启动快），true=同时写文件
    const val ENABLE_FILE_LOG = true

    fun init(ctx: Context) {
        if (!ENABLE_FILE_LOG) return
        try {
            val dir = ctx.getExternalFilesDir(null)
            logFile = File(dir, "xiaohei.txt")
            logFile?.let { if (it.exists()) it.delete() }
            logFile?.createNewFile()
            android.util.Log.d("LogUtil", "日志文件: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("LogUtil", "init error: ${e.message}")
        }
    }

    fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
        if (ENABLE_FILE_LOG) write("D/$tag: $msg")
    }

    fun e(tag: String, msg: String) {
        android.util.Log.e(tag, msg)
        if (ENABLE_FILE_LOG) write("E/$tag: $msg")
    }

    fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
        if (ENABLE_FILE_LOG) write("I/$tag: $msg")
    }

    private fun write(msg: String) {
        try {
            val f = logFile ?: return
            val time = df.format(Date())
            FileWriter(f, true).use { it.write("$time $msg\n") }
        } catch (_: Exception) {}
    }
}
