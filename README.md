# BT Finder

Kleine Android-App: scannt Bluetooth-LE-Geräte in der Umgebung und zeigt Name, Adresse,
Hersteller und Signalstärke (RSSI, geglättet) live an. Gerät antippen = "Wünschelruten-Modus"
mit großer dBm-Anzeige, Balken und Piepton (schneller = näher).

Grenzen: Nur Geräte, die gerade senden, sind sichtbar. Viele Geräte (u. a. Apple) nutzen
wechselnde MAC-Adressen, daher ist der Name der verlässlichere Anhaltspunkt.

## APK bauen
GitHub → Actions → "Build APK" → Artifact `BtFinder-debug-apk` herunterladen, auf dem Handy
installieren (Installation aus unbekannten Quellen erlauben). Lokal: `gradle assembleDebug`.

---

# Beleg-Scan

Zweite, eigenständige Android-App im Modul `belegscan/`: Quittung mit dem Handy scannen,
Belegblatt als PDF erzeugen und an die Buchhaltung mailen.

Ablauf:
1. **Quittung scannen** – Kamera mit Kantenerkennung und Zuschnitt (Google-Dokumentenscanner).
2. Datum, Händler, Betrag, MwSt. und bei Tankbelegen Sorte/Liter werden **auf dem Gerät**
   ausgelesen und als Vorschlag eingetragen – kurz prüfen, Kostenkonto wählen.
3. **PDF erstellen und senden** – A4-Blatt mit Firmenlogo, Belegdaten und Quittungsbild; die
   E-Mail-App öffnet sich mit hinterlegter Adresse, Betreff, Text und PDF im Anhang.

Einmalig unter **Einstellungen**: E-Mail-Adresse der Buchhaltung, Firmenname, eigener Name,
Logo (PNG/JPG) und die Kostenkonten (eines pro Zeile, z. B. `4530 Kfz-Betriebskosten`).

Grenzen: Die Erkennung ist ein Vorschlag, keine Garantie – Betrag und Datum vor dem Senden
prüfen. Der Scanner braucht Google Play-Dienste. Es werden keine Daten an Dritte übertragen;
erst der Mailversand verlässt das Gerät.

APK: GitHub → Actions → "Build APK" → Artifact `BelegScan-debug-apk`.
