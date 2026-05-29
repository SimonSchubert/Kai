package com.inspiredandroid.kai.tools

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import com.inspiredandroid.kai.shared.R
import java.io.ByteArrayOutputStream
import kotlin.math.max

class FloatingBrowserService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var webView: WebView? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): FloatingBrowserService = this@FloatingBrowserService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun showBrowser() {
        if (floatingView != null) return

        val params = WindowManager.LayoutParams(
            800,
            1000,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_browser, null)

        webView = floatingView?.findViewById(R.id.webView)
        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }

        val closeButton = floatingView?.findViewById<ImageButton>(R.id.closeButton)
        closeButton?.setOnClickListener { hideBrowser() }

        val dragView = floatingView?.findViewById<View>(R.id.titleBar)
        dragView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        val resizeButton = floatingView?.findViewById<View>(R.id.resizeHandle)
        resizeButton?.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = params.width
                        initialHeight = params.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = max(400, initialWidth + (event.rawX - initialTouchX).toInt())
                        params.height = max(400, initialHeight + (event.rawY - initialTouchY).toInt())
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(floatingView, params)
            }
            false
        }

        windowManager.addView(floatingView, params)
    }

    fun hideBrowser() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
            webView = null
        }
    }

    fun loadUrl(url: String) {
        if (floatingView == null) {
            showBrowser()
        }
        webView?.post { webView?.loadUrl(url) }
    }

    fun evaluateJavascript(script: String, callback: ValueCallback<String>) {
        webView?.post { webView?.evaluateJavascript(script, callback) }
    }

    fun captureScreenshot(callback: (String) -> Unit) {
        webView?.post {
            val bitmap = Bitmap.createBitmap(webView!!.width, webView!!.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView!!.draw(canvas)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val encoded = Base64.encodeToString(byteArray, Base64.DEFAULT)
            callback(encoded)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBrowser()
    }
}
