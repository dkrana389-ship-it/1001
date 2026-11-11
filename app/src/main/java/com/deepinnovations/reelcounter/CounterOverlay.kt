package com.deepinnovations.reelcounter

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Camera cutout (punch-hole / notch) ke paas dikhne wala chhota black pill.
 * - Punch-hole beech mein: pill camera ke aaspaas (Dynamic Island jaisa)
 * - Wide notch: notch ke theek neeche
 * - Corner camera ya cutout nahi: status bar ke beech mein
 * Touch bilkul nahi rokta (Instagram ka scroll normal chalta hai).
 */
class CounterOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private var attached = false
    private val root = LinearLayout(service)
    private val label = TextView(service)
    private val gapView = View(service)
    private val countText = TextView(service)
    private val bg = GradientDrawable()

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    init {
        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        bg.setColor(Color.BLACK)
        bg.setStroke(dp(1), 0x33FFFFFF)

        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.background = bg
        root.setPadding(dp(12), 0, dp(12), 0)

        label.text = "REELS"
        label.textSize = 9f
        label.letterSpacing = 0.15f
        label.gravity = Gravity.CENTER
        label.setTextColor(0xFF9A9AA3.toInt())

        countText.textSize = 15f
        countText.typeface = Typeface.DEFAULT_BOLD
        countText.gravity = Gravity.CENTER
        countText.setTextColor(Color.WHITE)

        root.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(gapView, LinearLayout.LayoutParams(dp(8), 1))
        root.addView(countText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else service.resources.displayMetrics.widthPixels

    private fun topCutout(): Rect? {
        return try {
            var cutout: android.view.DisplayCutout? = null
            if (Build.VERSION.SDK_INT >= 30) {
                cutout = wm.currentWindowMetrics.windowInsets.displayCutout
            }
            if (cutout == null && Build.VERSION.SDK_INT >= 28) {
                cutout = root.rootWindowInsets?.displayCutout
            }
            cutout?.boundingRects?.firstOrNull { it.top <= dp(2) && it.width() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private fun setSideWidth(w: Int) {
        (label.layoutParams as LinearLayout.LayoutParams).width = w
        (countText.layoutParams as LinearLayout.LayoutParams).width = w
    }

    private fun applyLayout() {
        val sw = screenWidth()
        val rect = topCutout()

        var pillH = dp(28)
        var gap = dp(8)
        var sideW = LinearLayout.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = dp(3)

        if (rect != null) {
            pillH = (rect.height() + dp(10)).coerceIn(dp(26), dp(38))
            val centered = abs(rect.centerX() - sw / 2) < sw * 0.12f
            if (centered && rect.width() <= sw * 0.14f) {
                // punch-hole beech mein: pill camera ke aaspaas
                gap = rect.width() + dp(14)
                sideW = dp(46)
                params.x = rect.centerX() - sw / 2
                params.y = (rect.centerY() - pillH / 2).coerceAtLeast(0)
            } else if (centered) {
                // chaura notch: notch ke theek neeche
                params.y = rect.bottom + dp(2)
            } else {
                // corner camera: status bar ke beech mein
                params.y = (rect.centerY() - pillH / 2).coerceAtLeast(0)
            }
        }

        params.height = pillH
        bg.cornerRadius = pillH / 2f
        (gapView.layoutParams as LinearLayout.LayoutParams).width = gap
        setSideWidth(sideW)
        root.requestLayout()
        if (attached) {
            try {
                wm.updateViewLayout(root, params)
            } catch (_: Exception) {
            }
        }
    }

    fun show(count: Int) {
        countText.text = "$count"
        applyLayout()
        if (attached) return
        try {
            wm.addView(root, params)
            attached = true
            root.post { applyLayout() }
        } catch (_: Exception) {
        }
    }

    fun hide() {
        if (!attached) return
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
        attached = false
    }

    fun update(count: Int) {
        countText.text = "$count"
        countText.animate().scaleX(1.25f).scaleY(1.25f).setDuration(90).withEndAction {
            countText.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }
}
