package com.xiaoheicoin.wallet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.ImageView
import com.caverock.androidsvg.SVG
import java.util.concurrent.ConcurrentHashMap

/** SVG运行时加载器：从assets读取SVG渲染成Bitmap，带内存缓存 */
object SvgLoader {
    private val cache = object : android.util.LruCache<String, Bitmap>(60) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 加载SVG并设置到ImageView（异步，Bitmap渲染，带缓存） */
    fun load(iv: ImageView, assetPath: String) {
        val cached = cache.get(assetPath)
        if (cached != null) {
            iv.setImageBitmap(cached)
            return
        }
        Nav.io {
            try {
                val svg = SVG.getFromAsset(iv.context.assets, assetPath)
                val pic = svg.renderToPicture()
                val size = 128
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawPicture(pic, android.graphics.Rect(0, 0, size, size))
                cache.put(assetPath, bmp)
                Nav.ui { iv.setImageBitmap(bmp) }
            } catch (e: Exception) { }
        }
    }

    /** 获取所有图标文件名（不含路径，排除fixed推荐头像） */
    fun listAll(ctx: Context): List<String> {
        return try {
            ctx.assets.list("icons")?.filter { it.endsWith(".svg") && !it.startsWith("fixed_") }?.sorted() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    /** 图标分类 */
    enum class Category(val label: String, val prefix: String) {
        CARTOON("卡通", "cartoon_"),
        CRYPTO("币圈", "crypto_"),
        LETTER("字母", "letter_"),
        TECH("科技", "tech_");
        companion object {
            fun of(name: String): Category = when {
                name.startsWith("cartoon_") -> CARTOON
                name.startsWith("crypto_") -> CRYPTO
                name.startsWith("letter_") -> LETTER
                else -> TECH
            }
        }
    }

    /** 渐变配色分类（从文件名后缀提取） */
    fun gradientOf(name: String): String {
        val base = name.removeSuffix(".svg")
        val idx = base.indexOf('_', base.indexOf('_') + 1)
        return if (idx > 0) base.substring(idx + 1) else "default"
    }

    /** 按分类筛选图标 */
    fun filterBy(ctx: Context, category: Category? = null, gradient: String? = null): List<String> {
        return listAll(ctx).filter { name ->
            (category == null || Category.of(name) == category) &&
            (gradient == null || gradientOf(name) == gradient)
        }
    }

    /** 获取所有渐变配色名 */
    fun allGradients(ctx: Context): List<String> {
        return listAll(ctx).map { gradientOf(it) }.distinct().sorted()
    }

    /** 完整assets路径 */
    fun path(name: String) = "icons/$name"
}
