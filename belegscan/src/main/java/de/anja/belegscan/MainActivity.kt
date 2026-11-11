package de.anja.belegscan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ablauf: Quittung scannen -> Daten werden vorgeschlagen -> prüfen/ergänzen ->
 * PDF mit Logo erzeugen -> an die hinterlegte Buchhaltungs-Adresse mailen.
 */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private val scanFile: File get() = File(File(filesDir, "belege"), "scan.jpg")
    private var hasScan = false

    private lateinit var setupHint: TextView
    private lateinit var scanButton: Button
    private lateinit var preview: ImageView
    private lateinit var formPane: LinearLayout
    private lateinit var ocrHint: TextView
    private lateinit var dateEdit: EditText
    private lateinit var merchantEdit: EditText
    private lateinit var purposeEdit: EditText
    private lateinit var accountSpinner: Spinner
    private lateinit var grossEdit: EditText
    private lateinit var vatSpinner: Spinner
    private lateinit var vatInfo: TextView
    private lateinit var noteEdit: EditText

    private val vatRates = listOf(19, 7, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        buildUi()
        cleanOldPdfs()
        if (savedInstanceState?.getBoolean("hasScan") == true && scanFile.exists()) {
            hasScan = true
            showScan()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("hasScan", hasScan)
    }

    override fun onResume() {
        super.onResume()
        setupHint.visibility = if (settings.isComplete) View.GONE else View.VISIBLE
        fillAccounts(accountSpinner.selectedItem as? String ?: settings.lastAccount)
    }

    // ---------- Oberfläche ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.ACCENT)
        }
        head.addView(Ui.header(this, "Beleg-Scan").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = "Einstellungen"
            textSize = 15f
            setTextColor(Color.WHITE)
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p, p, p)
            setOnClickListener { openSettings() }
        })
        root.addView(head)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@MainActivity, 16)
            setPadding(p, p / 2, p, p)
        }

        setupHint = TextView(this).apply {
            text = "Bitte zuerst in den Einstellungen die E-Mail-Adresse der Buchhaltung " +
                "hinterlegen. Hier tippen."
            textSize = 15f
            setTextColor(Color.rgb(140, 70, 0))
            setBackgroundColor(Color.rgb(255, 243, 205))
            val p = Ui.dp(this@MainActivity, 12)
            setPadding(p, p, p, p)
            setOnClickListener { openSettings() }
        }
        content.addView(setupHint)

        scanButton = Ui.button(this, "Quittung scannen", primary = true).apply {
            setOnClickListener { startScan() }
        }
        content.addView(scanButton)

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@MainActivity, 220)
            ).apply { topMargin = Ui.dp(this@MainActivity, 10) }
        }
        content.addView(preview)

        formPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        ocrHint = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
        }
        formPane.addView(ocrHint)

        formPane.addView(Ui.label(this, "Belegdatum"))
        dateEdit = Ui.edit(this, "TT.MM.JJJJ", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        formPane.addView(dateEdit)

        formPane.addView(Ui.label(this, "Händler / Aussteller"))
        merchantEdit = Ui.edit(this, "z. B. Aral", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        formPane.addView(merchantEdit)

        formPane.addView(Ui.label(this, "Verwendungszweck"))
        purposeEdit = Ui.edit(this, "z. B. Tanken", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        formPane.addView(purposeEdit)

        formPane.addView(Ui.label(this, "Kostenkonto"))
        accountSpinner = Spinner(this)
        formPane.addView(accountSpinner)

        formPane.addView(Ui.label(this, "Betrag brutto in €"))
        grossEdit = Ui.edit(this, "0,00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            // Komma zulassen – die reine Zahlentastatur würde es sonst verwerfen
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789,.")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = updateVatInfo()
            })
        }
        formPane.addView(grossEdit)

        formPane.addView(Ui.label(this, "Mehrwertsteuersatz"))
        vatSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, vatRates.map { "$it %" }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = updateVatInfo()
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        formPane.addView(vatSpinner)
        vatInfo = TextView(this).apply { textSize = 14f; setTextColor(Color.DKGRAY) }
        formPane.addView(vatInfo)

        formPane.addView(Ui.label(this, "Bemerkung (optional, z. B. Kennzeichen oder Projekt)"))
        noteEdit = Ui.edit(this, "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        formPane.addView(noteEdit)

        formPane.addView(Ui.button(this, "PDF erstellen und an Buchhaltung senden", primary = true).apply {
            setOnClickListener { send() }
        })
        formPane.addView(Ui.button(this, "Verwerfen / neuer Beleg").apply {
            setOnClickListener { reset() }
        })

        content.addView(formPane)
        root.addView(ScrollView(this).apply { addView(content) })
        setContentView(root)
    }

    private fun fillAccounts(select: String) {
        val list = settings.accounts
        accountSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
        val idx = list.indexOf(select)
        if (idx >= 0) accountSpinner.setSelection(idx)
    }

    private fun updateVatInfo() {
        val gross = ReceiptParser.parseCents(grossEdit.text.toString())
        val rate = vatRates[vatSpinner.selectedItemPosition.coerceIn(0, vatRates.lastIndex)]
        vatInfo.text = if (gross == null) "" else {
            val vat = ReceiptParser.vatCents(gross, rate)
            "darin MwSt. ${ReceiptParser.formatCents(vat)} €  ·  netto ${ReceiptParser.formatCents(gross - vat)} €"
        }
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    // ---------- Scannen ----------

    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                try {
                    startIntentSenderForResult(sender, REQ_SCAN, null, 0, 0, 0)
                } catch (e: Exception) {
                    toast("Scanner konnte nicht gestartet werden.")
                }
            }
            .addOnFailureListener { e ->
                toast("Scanner nicht verfügbar (Google Play-Dienste nötig): ${e.message}")
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SCAN || resultCode != RESULT_OK) return
        val uri = data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
            ?.pages?.firstOrNull()?.imageUri
        if (uri == null) {
            toast("Kein Scan erhalten.")
            return
        }
        try {
            scanFile.parentFile?.mkdirs()
            contentResolver.openInputStream(uri)!!.use { input ->
                scanFile.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            toast("Scan konnte nicht gespeichert werden.")
            return
        }
        hasScan = true
        clearFields()
        showScan()
        recognize()
    }

    private fun showScan() {
        preview.setImageBitmap(Ui.loadBitmap(this, scanFile, 1200))
        preview.visibility = View.VISIBLE
        formPane.visibility = View.VISIBLE
        scanButton.text = "Neu scannen"
    }

    private fun recognize() {
        ocrHint.text = "Quittung wird ausgelesen …"
        val image = try {
            InputImage.fromFilePath(this, Uri.fromFile(scanFile))
        } catch (e: Exception) {
            ocrHint.text = "Auslesen nicht möglich – bitte Felder von Hand ausfüllen."
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { result ->
                val pieces = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                    line.boundingBox?.let { OcrPiece(line.text, it.left, it.top, it.bottom) }
                }
                applyData(ReceiptParser.parse(ReceiptParser.groupRows(pieces)))
            }
            .addOnFailureListener {
                ocrHint.text = "Auslesen nicht möglich – bitte Felder von Hand ausfüllen."
            }
    }

    private fun applyData(d: ReceiptData) {
        dateEdit.setText(d.date)
        merchantEdit.setText(d.merchant)
        purposeEdit.setText(d.purpose)
        grossEdit.setText(d.grossCents?.let { ReceiptParser.formatCents(it) } ?: "")
        vatSpinner.setSelection(vatRates.indexOf(d.vatRate).coerceAtLeast(0))
        if (d.isFuel) {
            val list = settings.accounts
            val idx = list.indexOfFirst { it.contains("kraftstoff", true) }
                .takeIf { it >= 0 } ?: list.indexOfFirst { it.contains("kfz", true) }
            if (idx >= 0) accountSpinner.setSelection(idx)
        }
        val missing = listOfNotNull(
            "Datum".takeIf { d.date.isEmpty() },
            "Betrag".takeIf { d.grossCents == null },
        )
        ocrHint.text = buildString {
            append("Vorschlag aus der Quittung – bitte kurz prüfen.")
            if (missing.isNotEmpty()) append(" Nicht erkannt: ${missing.joinToString(", ")}.")
            if (d.mixedVat) append(" Mehrere Steuersätze auf dem Beleg – MwSt. bitte prüfen.")
        }
        updateVatInfo()
    }

    private fun clearFields() {
        listOf(dateEdit, merchantEdit, purposeEdit, grossEdit, noteEdit).forEach {
            it.setText("")
            it.error = null
        }
        vatSpinner.setSelection(0)
        fillAccounts(settings.lastAccount)
    }

    private fun reset() {
        hasScan = false
        scanFile.delete()
        clearFields()
        preview.setImageBitmap(null)
        preview.visibility = View.GONE
        formPane.visibility = View.GONE
        scanButton.text = "Quittung scannen"
    }

    // ---------- PDF + Versand ----------

    private fun send() {
        if (!settings.isComplete) {
            toast("Bitte zuerst die E-Mail-Adresse der Buchhaltung hinterlegen.")
            openSettings()
            return
        }
        val date = dateEdit.text.toString().trim()
        if (date.isEmpty()) {
            dateEdit.error = "Belegdatum fehlt"
            dateEdit.requestFocus()
            return
        }
        val gross = ReceiptParser.parseCents(grossEdit.text.toString())
        if (gross == null || gross <= 0) {
            grossEdit.error = "Betrag fehlt oder ist ungültig"
            grossEdit.requestFocus()
            return
        }
        val receipt = Ui.loadBitmap(this, scanFile, 1600)
        if (!hasScan || receipt == null) {
            toast("Bitte zuerst eine Quittung scannen.")
            return
        }
        val account = accountSpinner.selectedItem as? String ?: ""
        settings.lastAccount = account
        val voucher = Voucher(
            company = settings.company,
            address = settings.address,
            vatId = settings.vatId,
            taxNumber = settings.taxNumber,
            date = date,
            merchant = merchantEdit.text.toString().trim(),
            purpose = purposeEdit.text.toString().trim(),
            account = account,
            grossCents = gross,
            vatRate = vatRates[vatSpinner.selectedItemPosition.coerceIn(0, vatRates.lastIndex)],
            note = noteEdit.text.toString().trim(),
            submitter = settings.submitter,
            createdAt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date()),
        )
        val pdf = File(File(filesDir, "belege"), fileName(voucher))
        try {
            PdfBuilder.write(pdf, voucher, Ui.loadLogo(this, settings, 1200), receipt)
        } catch (e: Exception) {
            toast("PDF konnte nicht erstellt werden: ${e.message}")
            return
        }
        mail(pdf, voucher)
    }

    private fun fileName(v: Voucher): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY).format(Date())
        val merchant = v.merchant.replace(Regex("[^\\p{L}\\d]+"), "-").trim('-').take(24)
        val amount = ReceiptParser.formatCents(v.grossCents).replace(',', '-')
        return listOf("Beleg", stamp, merchant, amount).filter { it.isNotEmpty() }.joinToString("_") + ".pdf"
    }

    private fun mail(pdf: File, v: Voucher) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", pdf)
        val amount = "${ReceiptParser.formatCents(v.grossCents)} €"
        val subject = listOf("Beleg", v.date, v.merchant, amount).filter { it.isNotEmpty() }.joinToString(" – ")
        val body = buildString {
            append("Hallo,\n\nanbei ein Beleg zur Buchung:\n\n")
            append("Datum: ${v.date}\n")
            if (v.merchant.isNotEmpty()) append("Händler: ${v.merchant}\n")
            if (v.purpose.isNotEmpty()) append("Verwendungszweck: ${v.purpose}\n")
            if (v.account.isNotEmpty()) append("Kostenkonto: ${v.account}\n")
            append("Betrag brutto: $amount (MwSt. ${v.vatRate} %)\n")
            if (v.note.isNotEmpty()) append("Bemerkung: ${v.note}\n")
            append("\nDie Originalquittung reiche ich nach.\n")
            if (v.submitter.isNotEmpty()) append("\nViele Grüße\n${v.submitter}\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(settings.email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Beleg", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            // Nur E-Mail-Apps anbieten
            startActivity(Intent(intent).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) })
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent.createChooser(intent, "Beleg senden mit …"))
            } catch (e2: ActivityNotFoundException) {
                toast("Keine App zum Senden gefunden.")
            }
        }
    }

    /** PDFs bleiben 60 Tage als Sicherheitskopie in der App, danach werden sie entfernt. */
    private fun cleanOldPdfs() {
        val limit = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
        File(filesDir, "belege").listFiles()
            ?.filter { it.name.endsWith(".pdf") && it.lastModified() < limit }
            ?.forEach { it.delete() }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_SCAN = 11
    }
}
