package de.anja.belegscan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/** Alles, was auf dem Belegblatt steht. */
data class Voucher(
    val company: String,
    val address: String,
    val vatId: String,
    val taxNumber: String,
    val date: String,
    val merchant: String,
    val purpose: String,
    val account: String,
    val grossCents: Long,
    val vatRate: Int,
    val note: String,
    val submitter: String,
    val createdAt: String,
)

/** Erzeugt eine A4-Seite: Kopf mit Logo, links die Belegdaten, rechts die gescannte Quittung. */
object PdfBuilder {

    private const val PAGE_W = 595f   // A4 in Punkt
    private const val PAGE_H = 842f
    private const val LEFT = 70f      // breiter Rand links zum Lochen/Abheften
    private const val RIGHT = 40f
    private const val TOP = 40f
    private const val BOTTOM = 40f
    private val accent = Color.rgb(31, 78, 121)

    fun write(target: File, v: Voucher, logo: Bitmap?, receipt: Bitmap) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
        try {
            draw(page.canvas, v, logo, receipt)
            doc.finishPage(page)
            target.parentFile?.mkdirs()
            target.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun draw(c: Canvas, v: Voucher, logo: Bitmap?, receipt: Bitmap) {
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint().apply { color = accent; strokeWidth = 1.5f }
        val thinPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }
        val contentW = PAGE_W - LEFT - RIGHT

        // ----- Kopf: Logo links, Firmenname rechts -----
        val headH = 60f
        if (logo != null) {
            fit(c, logo, RectF(LEFT, TOP, LEFT + 200f, TOP + headH), bitmapPaint, alignLeft = true, alignTop = false)
        }
        run {
            // Firmenname und Anschrift als Block, unten bündig mit dem Logo
            val x = if (logo == null) LEFT else LEFT + 215f
            val w = (PAGE_W - RIGHT - x).toInt()
            val align = if (logo == null) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
            val blocks = listOfNotNull(
                v.company.takeIf { it.isNotEmpty() }
                    ?.let { layout(it, text(if (logo == null) 18f else 12f, bold = true, color = accent), w, align) },
                v.address.takeIf { it.isNotEmpty() }
                    ?.let { layout(it, text(9f, color = Color.DKGRAY), w, align) },
            )
            var by = TOP + headH - blocks.sumOf { it.height }
            for (b in blocks) {
                c.save(); c.translate(x, by); b.draw(c); c.restore()
                by += b.height
            }
        }
        var y = TOP + headH + 10f
        c.drawLine(LEFT, y, PAGE_W - RIGHT, y, linePaint)
        y += 14f

        // ----- Titel -----
        val title = layout("Beleg zur Buchhaltung", text(17f, bold = true), contentW.toInt())
        c.save(); c.translate(LEFT, y); title.draw(c); c.restore()
        y += title.height + 14f
        val bodyTop = y

        // ----- Quittung: hochkant rechts neben den Daten, quer darunter -----
        val portrait = receipt.height >= receipt.width
        val gap = 18f
        val dataW = if (portrait) contentW * 0.52f else contentW
        if (portrait) {
            val box = RectF(LEFT + dataW + gap, bodyTop, PAGE_W - RIGHT, PAGE_H - BOTTOM - 16f)
            fit(c, receipt, box, bitmapPaint, alignLeft = false, alignTop = true)
        }

        // ----- Daten -----
        val vat = ReceiptParser.vatCents(v.grossCents, v.vatRate)
        val rows = listOf(
            "Belegdatum" to v.date,
            "Händler / Aussteller" to v.merchant,
            "Verwendungszweck" to v.purpose,
            "Kostenkonto" to v.account,
            "Betrag brutto" to "${ReceiptParser.formatCents(v.grossCents)} €",
            "darin MwSt. ${v.vatRate} %" to "${ReceiptParser.formatCents(vat)} €",
            "Betrag netto" to "${ReceiptParser.formatCents(v.grossCents - vat)} €",
            "Bemerkung" to v.note,
            "Eingereicht von" to v.submitter,
            "Erfasst am" to v.createdAt,
        ).filter { it.second.isNotBlank() }

        val labelPaint = text(8.5f, color = Color.DKGRAY)
        val valuePaint = text(12f)
        val strongPaint = text(14f, bold = true)
        for ((label, value) in rows) {
            val l = layout(label, labelPaint, dataW.toInt())
            c.save(); c.translate(LEFT, y); l.draw(c); c.restore()
            y += l.height + 1f
            val vl = layout(value, if (label == "Betrag brutto") strongPaint else valuePaint, dataW.toInt())
            c.save(); c.translate(LEFT, y); vl.draw(c); c.restore()
            y += vl.height + 5f
            c.drawLine(LEFT, y, LEFT + dataW, y, thinPaint)
            y += 7f
        }

        // ----- Felder für die Buchhaltung -----
        y += 10f
        val hint = text(8.5f, color = Color.DKGRAY)
        for (label in listOf("Gebucht am / Kürzel", "Buchungs-Nr.")) {
            y += 26f
            c.drawLine(LEFT, y, LEFT + dataW.coerceAtMost(260f), y, Paint().apply { color = Color.GRAY; strokeWidth = 0.7f })
            c.drawText(label, LEFT, y + 10f, hint)
            y += 12f
        }
        y += 14f

        if (!portrait) {
            val box = RectF(LEFT, y, PAGE_W - RIGHT, PAGE_H - BOTTOM - 16f)
            if (box.height() > 60f) fit(c, receipt, box, bitmapPaint, alignLeft = true, alignTop = true)
        }

        // ----- Fußzeile -----
        c.drawText(
            "Originalquittung bitte an dieses Blatt heften.",
            LEFT, PAGE_H - BOTTOM, text(8f, color = Color.GRAY)
        )
        val tax = listOfNotNull(
            v.vatId.takeIf { it.isNotEmpty() }?.let { "USt-IdNr. $it" },
            v.taxNumber.takeIf { it.isNotEmpty() }?.let { "St.-Nr. $it" },
        ).joinToString("  ·  ")
        if (tax.isNotEmpty()) {
            val tp = text(8f, color = Color.GRAY).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(tax, PAGE_W - RIGHT, PAGE_H - BOTTOM, tp)
        }
    }

    private fun text(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun layout(
        s: String, paint: TextPaint, width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, paint, width.coerceAtLeast(1))
            .setAlignment(align)
            .build()

    /** Zeichnet das Bild seitenrichtig in die Box, ohne es zu vergrößern-verzerren. */
    private fun fit(c: Canvas, bmp: Bitmap, box: RectF, paint: Paint, alignLeft: Boolean, alignTop: Boolean) {
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val x = if (alignLeft) box.left else box.left + (box.width() - w) / 2f
        val y = if (alignTop) box.top else box.top + (box.height() - h) / 2f
        c.drawBitmap(bmp, null, RectF(x, y, x + w, y + h), paint)
    }
}
