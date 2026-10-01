package com.xiaoheibit.wallets

import android.content.Context
import android.content.res.Resources

/** Gem Wallet 精确配色（从源码 Theme.kt 提取） */
object C {
    val BG = 0xFF24262A.toInt()          // background
    val TABBAR = 0xFF1A191A.toInt()       // surface（底部导航）
    val CARD = 0xFF1A191A.toInt()         // surface（卡片）
    val CARD2 = 0xFF2C2C2E.toInt()        // surfaceContainerHighest
    val SHEET = 0xFF1A191A.toInt()        // surface（弹窗）
    val TXT = 0xFFFFFFFF.toInt()          // onSurface / onPrimary
    val SUB = 0xFF818181.toInt()          // onBackground
    val LINE = 0xFF3A3A3C.toInt()         // outlineVariant
    val BLUE = 0xFF2D5BE6.toInt()         // primary
    val BLUE2 = 0xFF3D6BF0.toInt()        // primary light
    val BLUE3 = 0xFF5B83F5.toInt()        // primary lighter
    val GREEN = 0xFF1B9A6C.toInt()        // tertiary
    val RED = 0xFFF84E4E.toInt()          // error
    val CYAN = 0xFF808D99.toInt()         // secondary
    val ORANGE = 0xFFFF9314.toInt()       // pendingColor
    val OUTLINE = 0xFF767A81.toInt()      // outline
    val SCRIM = 0xFF34373D.toInt()        // scrim
}

/** dp -> px */
fun Int.dp(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
fun Float.dp(): Float = this * Resources.getSystem().displayMetrics.density
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
