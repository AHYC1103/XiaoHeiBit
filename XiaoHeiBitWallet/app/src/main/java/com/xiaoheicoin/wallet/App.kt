package com.xiaoheicoin.wallet

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局异常捕获，确保闪退时崩溃栈写到日志
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                LogUtil.e("CRASH", "Uncaught exception in thread: ${thread.name}")
                LogUtil.e("CRASH", throwable.stackTraceToString())
            } catch (_: Exception) {}
            // 交给系统默认处理（闪退）
            defaultHandler?.uncaughtException(thread, throwable)
        }
        ReownWC.init(this)
    }

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
}
