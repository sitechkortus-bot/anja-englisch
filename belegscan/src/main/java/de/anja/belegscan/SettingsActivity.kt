package de.anja.belegscan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Einmalige Einrichtung: Firma, Empfänger-Adresse, Logo, Kostenkonten. */
class SettingsActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var company: EditText
    private lateinit var email: EditText
    private lateinit var submitter: EditText
    private lateinit var vatId: EditText
    private lateinit var taxNumber: EditText
    private lateinit var accounts: EditText
    private lateinit var logoView: ImageView
    private lateinit var logoHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.header(this, "Einstellungen"))

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@SettingsActivity, 16)
            setPadding(p, 0, p, p)
        }

        form.addView(Ui.label(this, "E-Mail-Adresse der Buchhaltung"))
        email = Ui.edit(
            this, "buchhaltung@firma.de",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        ).apply { setText(settings.email) }
        form.addView(email)

        form.addView(Ui.label(this, "Firmenname (steht im Kopf des Belegblatts)"))
        company = Ui.edit(this, "Firma GmbH").apply { setText(settings.company) }
        form.addView(company)

        form.addView(Ui.label(this, "USt-IdNr. (steht in der Fußzeile des Belegblatts)"))
        vatId = Ui.edit(this, "DE123456789", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
            .apply { setText(settings.vatId) }
        form.addView(vatId)

        form.addView(Ui.label(this, "Steuernummer"))
        taxNumber = Ui.edit(this, "123/456/78901").apply { setText(settings.taxNumber) }
        form.addView(taxNumber)

        form.addView(Ui.label(this, "Dein Name (\"Eingereicht von\")"))
        submitter = Ui.edit(
            this, "Vorname Nachname",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        ).apply { setText(settings.submitter) }
        form.addView(submitter)

        form.addView(Ui.label(this, "Firmenlogo"))
        logoView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@SettingsActivity, 70)
            ).apply { topMargin = Ui.dp(this@SettingsActivity, 6) }
        }
        logoHint = TextView(this).apply { textSize = 14f }
        form.addView(logoView)
        form.addView(logoHint)
        form.addView(Ui.button(this, "Logo auswählen …").apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                try {
                    startActivityForResult(intent, REQ_LOGO)
                } catch (e: Exception) {
                    toast("Keine App zur Bildauswahl gefunden.")
                }
            }
        })
        form.addView(Ui.button(this, "Logo entfernen").apply {
            setOnClickListener { settings.logoFile.delete(); showLogo() }
        })

        form.addView(Ui.label(this, "Kostenkonten – eines pro Zeile, z. B. \"4530 Kfz-Betriebskosten\""))
        accounts = Ui.edit(
            this, "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        ).apply {
            setText(settings.accountsText)
            minLines = 6
            gravity = Gravity.TOP
            textSize = 15f
        }
        form.addView(accounts)

        form.addView(Ui.button(this, "Speichern", primary = true).apply {
            setOnClickListener { save() }
        })

        root.addView(ScrollView(this).apply { addView(form) })
        setContentView(root)
        showLogo()
    }

    private fun showLogo() {
        val bmp = Ui.loadBitmap(this, settings.logoFile, 600)
        logoView.setImageBitmap(bmp)
        logoView.visibility = if (bmp != null) android.view.View.VISIBLE else android.view.View.GONE
        logoHint.text = if (bmp != null) "" else "Noch kein Logo hinterlegt."
        logoHint.visibility = if (bmp != null) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun save() {
        val mail = email.text.toString().trim()
        if (mail.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            email.error = "Bitte eine gültige E-Mail-Adresse eingeben"
            email.requestFocus()
            return
        }
        settings.email = mail
        settings.company = company.text.toString()
        settings.submitter = submitter.text.toString()
        settings.vatId = vatId.text.toString()
        settings.taxNumber = taxNumber.text.toString()
        settings.accountsText = accounts.text.toString()
        toast("Gespeichert")
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_LOGO || resultCode != RESULT_OK || uri == null) return
        try {
            // Kopie in den App-Speicher, damit das Logo auch nach dem Löschen der Quelle bleibt.
            val tmp = java.io.File(filesDir, "logo.tmp")
            contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (Ui.loadBitmap(this, tmp, 600) == null) {
                tmp.delete()
                toast("Diese Datei ist kein lesbares Bild (PNG oder JPG verwenden).")
                return
            }
            settings.logoFile.delete()
            tmp.renameTo(settings.logoFile)
            showLogo()
        } catch (e: Exception) {
            toast("Logo konnte nicht übernommen werden.")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_LOGO = 21
    }
}
