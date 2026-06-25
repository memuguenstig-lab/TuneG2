# KirinControl - KuKirin G2 E-Scooter BLE Cockpit & Tuning App

KirinControl ist eine moderne, native Android-Applikation (entwickelt in Kotlin und Jetpack Compose), die sich über Bluetooth Low Energy (BLE) mit dem KuKirin G2 E-Scooter verbindet. Sie bietet ein performantes Echtzeit-Cockpit, Tour-Aufzeichnungen, Live-Graphen sowie Tuning-Funktionen wie die Aufhebung der Geschwindigkeitsbegrenzung (vMax ~65 km/h) und die Steuerung des Allradantriebs (Dual Motor).

---

## 🏎️ Features der App

1. **Echtzeit-Cockpit (Dashboard)**
   * **Analoger Sport-Tacho**: Hochpräziser, flüssiger Geschwindigkeits-Arc mit digitaler Geschwindigkeitsanzeige.
   * **Detaillierte Telemetriedaten**: Live-Anzeige von Akkustand (%), exakter Spannung (V), Stromverbrauch (A), aktueller Leistung (W), Controller-Temperatur (°C) und Kilometerstand (km).
   * **Fehler-Zentrale**: Automatische Anzeige und verständliche Übersetzung von Scooter-Fehlercodes (E-01 bis E-08) im Pannenfall.

2. **Tuning- & Hardware-Steuerung**
   * **Modus-Umschalter (Gänge)**: Schnelle Gangwahl für G1 (Eco), G2 (Sport) und G3 (Turbo).
   * **Speed-Limit Unlock**: Bypasst die gesetzliche 20/25 km/h Sperre des KuKirin G2, um Geschwindigkeiten von bis zu 65 km/h freizuschalten.
   * **Allradantrieb (Dual Motor Mode)**: Schaltet beide Motoren gleichzeitig frei, um maximale Beschleunigung und Steigfähigkeit zu ermöglichen.
   * **Sicherheitstoggles**: Lichtsteuerung (An/Aus) und elektronisches Schloss (Scooter sperren/entsperren).

3. **Interaktiver Echtzeit-Simulator (In-App)**
   * **Integrierte Physik-Engine**: Ermöglicht vollwertiges Testen aller App-Komponenten (Tacho, Akkuverbrauch, Spannungsabfall unter Last, Temperaturanstieg und Kilometerzuwachs) direkt im Android-Emulator mittels Schiebereglern für Gas und Drucktasten für die Bremse.
   * **Fehlersimulator**: Diagnose-Fehler (wie Übertemperatur oder Hall-Sensor-Störung) können im Info-Tab manuell getriggert werden, um die visuelle Pannenwarnung der App im Cockpit zu testen.

4. **Fahrten-Logbuch (Lokale Datenbank)**
   * **Room-Datenbank**: Vollautomatische Offline-Speicherung und Verwaltung aller Fahrten.
   * **Statistik-Aggregator**: Auswertung von gefahrenen Kilometern, Fahrzeiten und allzeitiger Höchstgeschwindigkeit.
   * **Tour-Tracker**: Start-, Stop- und Lösch-Optionen für jede einzelne Fahrt.

5. **Echtzeit-Diagramm**
   * **Smooth Line Chart**: Hochperformantes Zeichnen von Live-Fahrdaten (Geschwindigkeit & Stromverbrauch) direkt auf ein Jetpack Compose Canvas Board.

6. **Dunkles und Helles Design**
   * **Cyber-Carbon Dark**: Dunkles Cockpit mit neon-grünen Elementen für beste Lesbarkeit bei Nachtfahrten.
   * **Athletic Silver Light**: Helles, kontrastreiches Thema für den Einsatz bei strahlendem Sonnenschein.

---

## 📂 Projektstruktur

```
/app/src/main/
│
├── AndroidManifest.xml          # Berechtigungen (BLE Scan, Connect, Location)
│
├── java/com/example/
│   ├── MainActivity.kt          # Einstiegspunkt, Tab-Manager & Berechtigungs-Prüfung
│   │
│   ├── data/
│   │   ├── model/
│   │   │   └── Models.kt        # Datenmodelle (ScooterTelemetry & RideLog)
│   │   │
│   │   └── local/
│   │       ├── RideDao.kt       # Room Database DAO
│   │       ├── AppDatabase.kt   # SQLite Database Instanz
│   │       └── RideRepository.kt# Repository Abstraktionsebene
│   │
│   ├── service/
│   │   └── ScooterBleManager.kt # Realer BLE Gatt Callback, UART Serial-Parser & Simulator-Physik
│   │
│   └── ui/
│       ├── ScooterViewModel.kt  # Zustands-Koordination (BLE State, Database, Chart-Ticker)
│       │
│       ├── screens/
│       │   ├── DashboardScreen.kt # Tacho-Arc, Tuning-Switches, Simulator-Bremse/Gas
│       │   ├── ScannerScreen.kt   # Radar-Scanner & BLE Gerätesuche
│       │   ├── ChartScreen.kt     # Custom Canvas Telemetrie-Diagramm
│       │   ├── HistoryScreen.kt   # Tour-Einträge, Gesamt-Statistiken
│       │   └── SettingsScreen.kt  # Dark/Light Switch, Code-Snippet, Fehler-Tabelle
│       │
│       └── theme/
│           ├── Color.kt         # Farb-Tokens (Neon-Green, Carbon-Gray, Sport-Teal)
│           ├── Theme.kt         # KirinTheme Material 3 Verknüpfung
│           └── Type.kt          # Typografie-Tokens
```

---

## 🛠️ Installations- & Build-Anleitung

### Voraussetzungen
* **Android Studio**: Ladybug (2024.2.1) oder neuer empfohlen.
* **JDK**: Version 17 oder neuer.
* **Android OS**: Android 7.0 (API Level 24) oder höher.
* **Physisches Gerät**: Für echtes BLE erforderlich (Emulatoren unterstützen in der Regel kein physisches Bluetooth-Scanning).

### Schritt-für-Schritt Installation

1. **Projekt in Android Studio öffnen**
   * Wähle `File -> Open` und navigiere zum Projektordner.
   * Warte, bis der Gradle-Sync abgeschlossen ist.

2. **Gradle Konfiguration prüfen**
   * Die App nutzt **Kotlin DSL** und den **Version Catalog (`libs.versions.toml`)** für optimales Abhängigkeitsmanagement.
   * Die Bibliotheken für **Room**, **Kotlin Coroutines** und **Material 3** werden automatisch aus MavenCentral heruntergeladen.

3. **Projekt Bauen und Starten**
   * Schließe dein physisches Android-Gerät über USB an (mit aktiviertem USB-Debugging) oder starte einen virtuellen Emulator.
   * Klicke in Android Studio auf den **grünen Play-Button (Run)** in der oberen Menüleiste.
   * Die App wird kompiliert, als APK signiert und auf dem Gerät installiert.

---

## 📡 BLE Daten-Protokoll & Telemetrie-Code (Beispiel)

Der KuKirin G2 sendet Telemetriedaten in kontinuierlichen Daten-Frames über eine serielle BLE-Charakteristik (z.B. Nordic UART Service TX). Ein Datenpaket startet mit dem Frame-Header `0xAA 0x55` zur sicheren Synchronisation.

Die in der App integrierte, sichere Parsing-Logik sieht wie folgt aus:

```kotlin
fun parseScooterData(data: ByteArray) {
    if (data.size < 5) return

    // Prüfe Frame-Header
    if (data[0] != 0xAA.toByte() || data[1] != 0x55.toByte()) {
        return
    }

    // 1. Geschwindigkeit auslesen (Einheit: 0,1 km/h)
    val speedRaw = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
    val speedKmh = speedRaw / 10.0f

    // 2. Akkustand in Prozent (0-100)
    val batteryPct = data[5].toInt() and 0xFF

    // 3. Systemspannung auslesen (Einheit: 0,1 V)
    val voltRaw = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
    val voltage = voltRaw / 10.0f

    // 4. Stromverbrauch & Temperatur
    val currentAmps = (((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)) / 10.0f
    val tempC = data[10].toInt().toFloat()
}
```

---

## ⚖️ Tuning Sicherheitshinweise & StVZO

* **Achtung**: Die Benutzung von getunten E-Scootern auf öffentlichen Straßen ist in vielen Ländern (u.a. Deutschland, Österreich, Schweiz) gesetzlich verboten. 
* **Keine Firmware-Flasher**: Diese App nimmt **KEINE permanenten Änderungen** an der internen Controller-Firmware vor. Alle Tuning-Befehle werden temporär als serielle BLE-Anweisungen gesendet und verfallen, sobald der Scooter aus- und wieder eingeschaltet wird. Dies verhindert dauerhafte Beschädigungen der Scooter-Hardware.
