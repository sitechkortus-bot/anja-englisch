package de.anja.belegscan

import android.content.Context
import java.io.File

/** Einmalige Angaben: Firma, Empfänger, Logo, Kostenkonten. Liegen nur auf dem Gerät. */
class Settings(private val context: Context) {

    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    var company: String
        get() = prefs.getString("company", "") ?: ""
        set(v) = prefs.edit().putString("company", v.trim()).apply()

    var email: String
        get() = prefs.getString("email", null) ?: DEFAULT_EMAIL
        set(v) = prefs.edit().putString("email", v.trim()).apply()

    var submitter: String
        get() = prefs.getString("submitter", "") ?: ""
        set(v) = prefs.edit().putString("submitter", v.trim()).apply()

    /** Ein Kostenkonto pro Zeile, z. B. "4530 Kfz-Betriebskosten". */
    var accountsText: String
        get() = prefs.getString("accounts", null) ?: DEFAULT_ACCOUNTS
        set(v) = prefs.edit().putString("accounts", v).apply()

    val accounts: List<String>
        get() = accountsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_ACCOUNTS.lines() }

    var lastAccount: String
        get() = prefs.getString("lastAccount", "") ?: ""
        set(v) = prefs.edit().putString("lastAccount", v).apply()

    val logoFile: File get() = File(context.filesDir, "logo.img")

    val isComplete: Boolean get() = email.isNotEmpty()

    companion object {
        /** Voreingestellter Empfänger; in den Einstellungen änderbar. */
        const val DEFAULT_EMAIL = "buchhaltung@sikosln.de"

        const val DEFAULT_ACCOUNTS =
            "Kfz-Betriebskosten (Kraftstoff)\nKfz-Reparaturen\nReisekosten\nBewirtung\n" +
                "Bürobedarf\nPorto\nWerkzeug / Kleinmaterial\nSonstiges"
    }
}
