package de.anja.belegscan

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Ein erkanntes Textstück mit Position (aus der Texterkennung). */
data class OcrPiece(val text: String, val left: Int, val top: Int, val bottom: Int)

/** Ergebnis der Auswertung. Alles sind Vorschläge, die der Nutzer im Formular korrigieren kann. */
data class ReceiptData(
    val merchant: String = "",
    val date: String = "",
    val grossCents: Long? = null,
    val vatRate: Int = 19,
    val mixedVat: Boolean = false,
    val isFuel: Boolean = false,
    val fuelType: String = "",
    val liters: String = "",
) {
    val purpose: String
        get() {
            if (!isFuel) return ""
            val details = listOf(fuelType, if (liters.isNotEmpty()) "$liters l" else "")
                .filter { it.isNotEmpty() }
            return if (details.isEmpty()) "Tanken" else "Tanken (${details.joinToString(", ")})"
        }
}

/** Reine Kotlin-Logik ohne Android-Abhängigkeiten, damit sie separat testbar ist. */
object ReceiptParser {

    /**
     * Die Texterkennung liefert Beschriftung ("Summe") und Betrag ("58,40") oft als getrennte
     * Stücke. Hier werden Stücke auf gleicher Höhe wieder zu einer Zeile zusammengesetzt.
     */
    fun groupRows(pieces: List<OcrPiece>): List<String> {
        val rows = ArrayList<MutableList<OcrPiece>>()
        for (p in pieces.sortedBy { (it.top + it.bottom) / 2 }) {
            val center = (p.top + p.bottom) / 2
            val height = (p.bottom - p.top).coerceAtLeast(1)
            val row = rows.lastOrNull()
            val ref = row?.first()
            if (ref != null && abs(center - (ref.top + ref.bottom) / 2) < height * 0.6) {
                row.add(p)
            } else {
                rows.add(mutableListOf(p))
            }
        }
        return rows.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    private val amountRegex = Regex("""(?<![\d,.])(\d{1,3}(?:[. ]\d{3})+|\d{1,5})[,.](\d{2})(?![\d])""")
    private val dateRegex = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})(?!\d)""")
    private val totalWords = Regex(
        """summe|gesamt|total|zu zahlen|zahlbetrag|endbetrag|rechnungsbetrag|betrag|brutto""",
        RegexOption.IGNORE_CASE
    )
    private val notTotalWords = Regex(
        """netto|mwst|mehrwert|ust|steuer|zwischensumme|gegeben|r[üu]ckgeld|zur[üu]ck|wechselgeld|rabatt""",
        RegexOption.IGNORE_CASE
    )
    private val paidWords = Regex("""gegeben|r[üu]ckgeld|zur[üu]ck|wechselgeld""", RegexOption.IGNORE_CASE)
    private val vatRegex = Regex("""(?<!\d)(19|7)(?:[,.]0{1,2})?\s*%""")
    private val litersRegex = Regex(
        """(\d{1,3}[,.]\d{1,2})\s*(?:l|ltr|liter)\b""", RegexOption.IGNORE_CASE
    )

    private val brands = listOf(
        "Aral", "Shell", "Esso", "TotalEnergies", "JET", "Star", "Agip", "Eni", "HEM", "Avia",
        "OMV", "bft", "Westfalen", "Tamoil", "Orlen", "Raiffeisen", "Hoyer", "Sprint", "Classic",
        "Q1", "Allguth", "Baywa", "Markant", "Nordoel", "Team", "Elan", "Oil!"
    )
    private val fuelTypes = listOf(
        "AdBlue" to Regex("""adblue""", RegexOption.IGNORE_CASE),
        "Diesel" to Regex("""diesel""", RegexOption.IGNORE_CASE),
        "Super E10" to Regex("""\be\s?10\b""", RegexOption.IGNORE_CASE),
        "Super Plus" to Regex("""super\s?plus""", RegexOption.IGNORE_CASE),
        "Super" to Regex("""\bsuper\b|\bbenzin\b|\be\s?5\b""", RegexOption.IGNORE_CASE),
        "Autogas" to Regex("""autogas|\blpg\b""", RegexOption.IGNORE_CASE),
        "Strom" to Regex("""\bkwh\b|ladevorgang""", RegexOption.IGNORE_CASE),
    )
    private val fuelWords = Regex(
        """tankstelle|kraftstoff|zapfs[äa]ule|\bs[äa]ule\b|zapfpunkt""", RegexOption.IGNORE_CASE
    )

    fun parse(rows: List<String>): ReceiptData {
        val brand = findBrand(rows)
        val fuelType = fuelTypes.firstOrNull { (_, rx) -> rows.any { rx.containsMatchIn(it) } }?.first ?: ""
        val isFuel = brand != null || fuelType.isNotEmpty() || rows.any { fuelWords.containsMatchIn(it) }
        val rates = rows.flatMap { r -> vatRegex.findAll(r).map { it.groupValues[1].toInt() }.toList() }.toSet()

        return ReceiptData(
            merchant = brand ?: firstTextRow(rows),
            date = findDate(rows),
            grossCents = findGross(rows),
            vatRate = if (rates.size == 1) rates.first() else 19,
            mixedVat = rates.size > 1,
            isFuel = isFuel,
            fuelType = fuelType,
            liters = if (isFuel) findLiters(rows) else "",
        )
    }

    private fun findBrand(rows: List<String>): String? {
        val head = rows.take(10)
        for (b in brands) {
            val rx = Regex("""(?<![\p{L}\d])${Regex.escape(b)}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
            if (head.any { rx.containsMatchIn(it) }) return b
        }
        return null
    }

    private fun firstTextRow(rows: List<String>): String =
        rows.firstOrNull { r -> r.count { it.isLetter() } >= 3 }?.trim()?.take(40) ?: ""

    private fun findDate(rows: List<String>): String {
        for (r in rows) {
            for (m in dateRegex.findAll(r)) {
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                var y = m.groupValues[3].toInt()
                if (y < 100) y += 2000
                if (d in 1..31 && mo in 1..12 && y in 2000..2099) {
                    return String.format(Locale.GERMANY, "%02d.%02d.%04d", d, mo, y)
                }
            }
        }
        return ""
    }

    private fun amounts(row: String): List<Long> = amountRegex.findAll(row).map {
        val euros = it.groupValues[1].replace(".", "").replace(" ", "").toLong()
        euros * 100 + it.groupValues[2].toLong()
    }.toList()

    private fun findGross(rows: List<String>): Long? {
        // 1. Zeilen mit "Summe", "Gesamt", "zu zahlen" ... – der größte Betrag daraus.
        //    Steht der Betrag in der Folgezeile, wird diese mit herangezogen.
        val labelled = ArrayList<Long>()
        rows.forEachIndexed { i, r ->
            if (totalWords.containsMatchIn(r) && !notTotalWords.containsMatchIn(r)) {
                val own = amounts(r)
                if (own.isNotEmpty()) labelled.addAll(own)
                else rows.getOrNull(i + 1)
                    ?.takeIf { !notTotalWords.containsMatchIn(it) }
                    ?.let { labelled.addAll(amounts(it)) }
            }
        }
        labelled.maxOrNull()?.let { return it }
        // 2. Sonst der größte Betrag auf dem Beleg, ohne "Gegeben"/"Rückgeld".
        return rows.filter { !paidWords.containsMatchIn(it) }.flatMap { amounts(it) }.maxOrNull()
    }

    private fun findLiters(rows: List<String>): String {
        for (r in rows) {
            litersRegex.find(r)?.let { return it.groupValues[1].replace('.', ',') }
        }
        return ""
    }

    // ---------- Beträge ----------

    fun formatCents(cents: Long): String =
        String.format(Locale.GERMANY, "%d,%02d", cents / 100, cents % 100)

    /** "58,40", "58.40", "1.234,56", "58" -> Cent; null bei ungültiger Eingabe. */
    fun parseCents(input: String): Long? {
        val s = input.trim().replace("€", "").replace(" ", "")
        if (s.isEmpty()) return null
        val sep = maxOf(s.lastIndexOf(','), s.lastIndexOf('.'))
        val hasDecimals = sep >= 0 && s.length - sep - 1 in 1..2
        val intPart = (if (hasDecimals) s.substring(0, sep) else s).replace(".", "").replace(",", "")
        val decPart = if (hasDecimals) s.substring(sep + 1).padEnd(2, '0') else "00"
        if (intPart.isEmpty() || !intPart.all { it.isDigit() } || !decPart.all { it.isDigit() }) return null
        return intPart.toLong() * 100 + decPart.toLong()
    }

    /** Im Bruttobetrag enthaltene Mehrwertsteuer. */
    fun vatCents(grossCents: Long, ratePercent: Int): Long =
        (grossCents.toDouble() * ratePercent / (100 + ratePercent)).roundToLong()
}
