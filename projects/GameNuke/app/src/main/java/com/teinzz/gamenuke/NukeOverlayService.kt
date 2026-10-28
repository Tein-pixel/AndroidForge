package com.teinzz.gamenuke

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class NukeOverlayService : Service() {
    private var wm: WindowManager? = null
    private var chip: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "✦ NUKE"
            setTextColor(Color.rgb(0, 255, 198))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.rgb(8, 22, 25))
            alpha = .95f
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        p.x = 18; p.y = 180
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; true }
                MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX - tx).toInt(); p.y = sy + (e.rawY - ty).toInt(); runCatching { wm?.updateViewLayout(view, p) }; true }
                MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX - tx) < 8 && kotlin.math.abs(e.rawY - ty) < 8) stopSelf(); true }
                else -> false
            }
        }
        chip = view
        runCatching { wm?.addView(view, p) }
    }

    override fun onDestroy() {
        chip?.let { runCatching { wm?.removeView(it) } }
        chip = null
        super.onDestroy()
    }
}
