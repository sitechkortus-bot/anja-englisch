package de.anja.btfinder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Einfacher Balken: 0f..1f gefüllt, Farbe von rot (weit) nach grün (nah). */
class SignalBar(context: Context) : View(context) {
    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A2A") }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, bg)
        if (level <= 0f) return
        fg.color = Color.HSVToColor(floatArrayOf(level * 120f, 0.85f, 0.95f))
        rect.set(0f, 0f, maxOf(height.toFloat(), width * level), height.toFloat())
        canvas.drawRoundRect(rect, r, r, fg)
    }
}
