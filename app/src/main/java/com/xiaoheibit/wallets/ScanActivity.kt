package com.xiaoheibit.wallets

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** 原生相机扫码（Camera1 + ZXing core），对应 webui startScan */
class ScanActivity : Activity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private lateinit var surface: SurfaceView
    private lateinit var loading: TextView
    private val reader = QRCodeReader()
    private val main = Handler(Looper.getMainLooper())
    private var decoded = false
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        val root = FrameLayout(this); root.setBackgroundColor(Color.BLACK)
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        surface.holder.addCallback(this)

        // Gem 风格四角扫描框
        val boxSize = 260.dp()
        val cornerLen = 50.dp()
        val cornerW = 6.dp()
        val corners = listOf(
            // 左上
            Triple(Gravity.TOP or Gravity.START, 0, 0),
            // 右上
            Triple(Gravity.TOP or Gravity.END, 0, 0),
            // 左下
            Triple(Gravity.BOTTOM or Gravity.START, 0, 0),
            // 右下
            Triple(Gravity.BOTTOM or Gravity.END, 0, 0)
        )
        corners.forEachIndexed { idx, (gravity, _, _) ->
            val corner = View(this)
            val isTop = idx < 2
            val isLeft = idx % 2 == 0
            corner.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                // 每个角是L形，用两个view拼
            }
            // 横线
            val hLine = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = cornerW / 2f
                }
                layoutParams = FrameLayout.LayoutParams(cornerLen, cornerW).apply {
                    this.gravity = gravity
                    if (isTop) topMargin = (resources.displayMetrics.heightPixels - boxSize) / 2
                    else bottomMargin = (resources.displayMetrics.heightPixels - boxSize) / 2
                    if (isLeft) leftMargin = (resources.displayMetrics.widthPixels - boxSize) / 2
                    else rightMargin = (resources.displayMetrics.widthPixels - boxSize) / 2
                }
            }
            root.addView(hLine)
            // 竖线
            val vLine = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFFFFFFF.toInt())
                    cornerRadius = cornerW / 2f
                }
                layoutParams = FrameLayout.LayoutParams(cornerW, cornerLen).apply {
                    this.gravity = gravity
                    if (isTop) topMargin = (resources.displayMetrics.heightPixels - boxSize) / 2
                    else bottomMargin = (resources.displayMetrics.heightPixels - boxSize) / 2
                    if (isLeft) leftMargin = (resources.displayMetrics.widthPixels - boxSize) / 2
                    else rightMargin = (resources.displayMetrics.widthPixels - boxSize) / 2
                }
            }
            root.addView(vLine)
        }
        loading = TextView(this)
        loading.text = "发送、支付或连接 DApp"
        loading.setTextColor(Color.WHITE); loading.textSize = 15f
        loading.gravity = Gravity.CENTER
        val llp = FrameLayout.LayoutParams(-2, -2); llp.gravity = Gravity.CENTER
        llp.bottomMargin = 220.dp(); loading.layoutParams = llp
        root.addView(loading)

        val close = TextView(this); close.text = "✕"; close.setTextColor(Color.WHITE); close.textSize = 20f
        close.gravity = Gravity.CENTER
        close.setBackgroundColor(0x99000000.toInt())
        val clp = FrameLayout.LayoutParams(44.dp(), 44.dp()); clp.gravity = Gravity.TOP or Gravity.END
        clp.topMargin = 44.dp(); clp.rightMargin = 16.dp(); close.layoutParams = clp
        close.setOnClickListener { finish() }
        root.addView(close)
        setContentView(root)

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1)
        }
    }

    private fun border(): android.graphics.drawable.GradientDrawable {
        val g = android.graphics.drawable.GradientDrawable()
        g.setColor(0x00000000)
        g.cornerRadius = 16.dp().toFloat()
        g.setStroke(3.dp(), C.BLUE2)
        return g
    }

    override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(r, p, g)
        if (g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) initCamera()
        else { loading.text = "未授予摄像头权限" }
    }

    override fun surfaceCreated(h: SurfaceHolder) { initCamera() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { stopCamera() }

    private fun initCamera() {
        if (started) return
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            camera = Camera.open(0)
            val params = camera!!.parameters
            val sizes = params.supportedPreviewSizes
            val pick = sizes.minByOrNull { kotlin.math.abs(it.width - 640) } ?: sizes[0]
            params.setPreviewSize(pick.width, pick.height)
            params.previewFormat = android.graphics.ImageFormat.NV21
            camera!!.parameters = params
            camera!!.setDisplayOrientation(90)
            camera!!.setPreviewDisplay(surface.holder)
            camera!!.startPreview()
            camera!!.setPreviewCallback { data, cam -> onFrame(data, cam.parameters.previewSize.width, cam.parameters.previewSize.height) }
            started = true
            loading.visibility = View.GONE
        } catch (e: Exception) {
            loading.text = "无法打开摄像头"
        }
    }

    private fun stopCamera() {
        try { camera?.stopPreview(); camera?.setPreviewCallback(null); camera?.release() } catch (_: Exception) {}
        camera = null; started = false
    }

    override fun onPause() { super.onPause(); stopCamera() }
    override fun onResume() {
        super.onResume()
        if (started.not() && surface.holder.surface.isValid) initCamera()
    }

    private fun onFrame(data: ByteArray, w: Int, h: Int) {
        if (decoded) return
        try {
            val rotated = rotateCW(data, w, h)
            val src = PlanarYUVLuminanceSource(rotated, h, w, 0, 0, h, w, false)
            val bmp = BinaryBitmap(HybridBinarizer(src))
            val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
            val res = reader.decode(bmp, hints)
            if (res != null && res.text != null) {
                decoded = true
                val text = res.text
                main.post {
                    ScanUtil.cb?.invoke(text)
                    ScanUtil.cb = null
                    finish()
                }
            }
        } catch (_: Exception) {
            reader.reset()
        }
    }

    /** NV21 Y 平面顺时针 90°，输出尺寸 h×w */
    private fun rotateCW(data: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            out[x * h + (h - 1 - y)] = data[y * w + x]
        }
        return out
    }
}
