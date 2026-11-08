# BT Finder

Kleine Android-App: scannt Bluetooth-LE-Geräte in der Umgebung und zeigt Name, Adresse,
Hersteller und Signalstärke (RSSI, geglättet) live an. Gerät antippen = "Wünschelruten-Modus"
mit großer dBm-Anzeige, Balken und Piepton (schneller = näher).

Grenzen: Nur Geräte, die gerade senden, sind sichtbar. Viele Geräte (u. a. Apple) nutzen
wechselnde MAC-Adressen, daher ist der Name der verlässlichere Anhaltspunkt.

## APK bauen
GitHub → Actions → "Build APK" → Artifact `BtFinder-debug-apk` herunterladen, auf dem Handy
installieren (Installation aus unbekannten Quellen erlauben). Lokal: `gradle assembleDebug`.
