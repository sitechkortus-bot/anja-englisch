package de.anja.belegscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** Kleine Helfer für die im Code gebauten Oberflächen. */
object Ui {
    val ACCENT = Color.rgb(31, 78, 121)

    fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    fun label(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(context, 12), 0, 0)
    }

    fun header(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setBackgroundColor(ACCENT)
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
    }

    fun edit(context: Context, hint: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(context).apply {
            this.hint = hint
            inputType = type
            textSize = 17f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    fun button(context: Context, text: String, primary: Boolean = false): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 17f
        if (primary) {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56)
        ).apply { topMargin = dp(context, 10) }
    }

    /** Lädt ein Bild verkleinert, damit große Scans den Speicher nicht sprengen. */
    fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    fun loadBitmap(context: Context, file: File, maxSide: Int): Bitmap? =
        if (file.exists()) loadBitmap(context, Uri.fromFile(file), maxSide) else null
}
