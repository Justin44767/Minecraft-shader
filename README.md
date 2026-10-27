# VERITY V5 – THE AI THAT WATCHES

**Kompilierbare Entwicklungsversion für Minecraft Java 26.3, Fabric und Java 25. Die vollständige V5-Spezifikation ist noch nicht umgesetzt.**

Artefakt: `build/libs/VerityV5-26.3-Fabric-dev.jar`. Kompilierter Java-Code mit Fabric-Metadaten, eigenen Assets und verschachtelten Laufzeitbibliotheken. Dies ist noch nicht die angeforderte fertige `VerityV5-26.3-Fabric.jar`.

Die bereits kompilierte [Entwicklungs-JAR liegt unter `downloads`](downloads/VerityV5-26.3-Fabric-dev.jar); auf der GitHub-Dateiseite kann sie heruntergeladen werden. Die [SHA-256-Prüfsumme](downloads/SHA256SUMS.txt) gehört genau zu dieser Datei. Quellcode und Buildanleitung sind im selben Repository enthalten.

Gewünschte Videoreferenz: https://youtu.be/f6f3PhauXyg. Der Benutzer erlaubt einen Loader-/Versionswechsel, wenn er technische Vorteile bringt. Der Inhalt des Videos ist aus dieser Cloud nicht abrufbar (HTTP 403); es wurde noch kein visueller oder spielerischer Vergleich durchgeführt. Fabric/26.3 bleibt vorerst das automatisch geprüfte Ziel. Die fehlenden Funktionen weiter unten werden durch einen bloßen Versionswechsel nicht ergänzt.

## Vorhanden

| Bereich | Stand |
|---|---|
| Fabric-Projekt | Gradle 9.8.2, Loom 1.19.3, Loader 0.20.1, API 0.165.0+26.3, Java 25; Wrapper mit SHA-256 |
| VERITY | Eigene serverseitige Mob-Entity, gemeinsamer UUID-Anspruch und Abweisung geladener Duplikate |
| Darstellung | Eigenes goldenes Kugelmodell, Textur, Blinzeln, Schwebepose, Blickkontakt und stimmungsabhängige Brauen/Mundpose; Client-Bild noch nicht geprüft |
| Follow | Serverseitige Flugnavigation, Follow/Stay und Aufmerksamkeit zum aktuellen Sprecher; keine dimensionsübergreifenden Teleports |
| Stimmung | Globaler Wert und persönliches Vertrauen pro UUID; Höflichkeit, Beleidigungen, Angriffe; rechts/links, vertikales/horizontales animiertes HUD |
| Gespräche | Begrenzte regelbasierte DE-/EN-Dialogengine, separate Historien, private/öffentliche Antworten und kurze Minecraft-Befehle; keine generative KI |
| Multiplayer-Kern | UUID/Sitzungstoken, begrenzte Queue, aktuelle Rechte bei Ausführung, Timeouts und Replay-Sperre für die letzten 256 Anfragen pro Spieler |
| Text zu Sprache | Separate lokale OS-Narrator-Instanz über Minecrafts Bibliothek; ohne Konto/KI-Server. Benötigt funktionierende OS-Stimme. Nicht räumlich, Audio noch nicht getestet |
| Voice-Eingabe | Optionaler offizieller SVC-Plugin-Entrypoint gegen API 2.6.25; Opus und Vosk im Hintergrund, explizite Zustimmung pro Verbindung. Ohne Modell noch nicht betriebsbereit |
| Startpaket | Zehn echte deutsche Buchseiten und eigenes Taschenlampen-Item; ausstehende Zustellung bei vollem Inventar |
| Taschenlampe | Eigene Geometrie/Textur, physischer ON/OFF-Zustand und Schaltgeräusch. **Noch kein Lichtkegel oder dynamisches Licht** |
| Tod | AFTER_DEATH-Hook, persistierter Anspruch, Belohnung erst bei echtem AFTER_RESPAWN; optionaler UUID-/sitzungsgebundener Kick nach Speicherung |
| Item Giving | Standard aus; serverseitige Inventarzustellung, Registry-IDs, DE-/EN-Aliasse, Menge, Host/Whitelist, Spielmodus, Höflichkeit, Cooldown |
| Speicher | Versionierte Datei pro Welt, SHA-256, atomarer Dateiersatz, begrenzter Hintergrundschreiber; kein stiller Reset bei Fehlern |
| Einstellungen | Lokales dunkles Menü mit lila Akzenten, Right Shift und Options-Schaltfläche; Host-Regeln in serverseitiger Properties-Datei |

**Noch nicht umgesetzt:** Story-Kiste, zusammenhängende Horror-Kampagne, 20 Horror-Ereignisse, tatsächliche Demon-Verwandlung und Angriffe, räumliche SVC-Sprachausgabe, Audio-Lippensynchronisation, Stimmen-/Originalstimmenimport, dynamischer Lichtkegel, eigener Death Screen, vollständige scrollbare Einstellungskategorien, Gruppen-Gesprächskontext und Spielanbindung der Abstimmungen. Die vier Story-Phasen sind bislang gespeicherte Zustandswerte, keine spielbare Kampagne.

**Essential ist weder gebündelt noch als kompatibel getestet.** Die Mod verwendet die vorhandene Host-Welt und erzeugt keine zusätzliche Serverinstanz. Das beweist noch keine funktionierenden Einladungen oder Voice-UDP-Verbindungen.

## Build

Ein Java-25-JDK installieren, `JAVA_HOME` darauf setzen, dann im Projektordner:

```bat
gradlew.bat clean build
```

Linux/macOS: `./gradlew clean build`. In dieser vorbereiteten Linux-Cloud:

```bash
python3 scripts/cloud_setup.py
python3 scripts/cloud_gradle.py clean build --no-daemon
```

Der Build führt Kerntests und Fabric-GameTests aus. Wrapper-Prüfsumme und TLS-Verifikation bleiben aktiv. Der Cloud-Helfer verwendet System-Truststore und bereitgestellten Proxy, benötigt keine Tokens und schreibt keine Proxy-Zugangsdaten. Quellen: `src/main`, `src/client`, `core/src/main`; Tests: `core/src/test`, `src/gametest`.

## Entwicklungsversion installieren

1. Launcher-Profil für Minecraft **26.3** mit Java **25** und passendem Fabric Loader anlegen.
2. Fabric API **0.165.0+26.3** und `VerityV5-26.3-Fabric-dev.jar` nach `mods` kopieren. Vosk und JNA sind enthalten; ein Sprachmodell nicht.
3. Eine separate Testwelt öffnen. Der erste Join vergibt Buch und Taschenlampe; volle Inventare werden später erneut geprüft.
4. Als Host/Operator `/verity spawn` ausführen. Eine automatische Kisten-Begegnung gibt es noch nicht.
5. `Verity, hallo!` im normalen Chat oder `/verity ask Verity, hallo!` schreiben. `/verity private <Text>` antwortet nur dem Absender. Öffentliche Antworten gehen an Spieler derselben Dimension im Radius von 32 Blöcken um den Fragenden; OS-Ausgabe ist nicht räumlich.
6. `/verity voice test` prüft die lokale OS-Stimme. Right Shift öffnet Anzeige-/Sprachoptionen. Fehlt die OS-Stimme, bleibt Text sichtbar.

Alle Multiplayer-Clients benötigen passende Fabric-Abhängigkeiten und VERITY. Eine separate offizielle, für 26.3 verfügbare Simple-Voice-Chat-Version wird nur für Mikrofoneingabe benötigt. Die API ist kein Ersatz für die eigentliche SVC-Mod. Essential ist optional und separat zu prüfen.

## Host-Regeln

Beim ersten Server-/Weltstart entsteht `config/verity-server.properties`. Änderungen gelten nach Welt-/Serverneustart. Auf Dedicated Servern legt `host_uuid` den Host fest; im integrierten Server wird der tatsächliche Weltbesitzer verwendet. Nachrichten/Namensbehauptungen verleihen keine Rechte.

Für Item Giving z. B.:

```properties
item_giving=true
host_only_items=true
max_item_amount=64
item_cooldown_seconds=30
politeness_required=true
```

Für freigegebene Freunde `host_only_items=false` und `item_whitelist` als kommagetrennte UUID-Liste setzen. Eine leere Whitelist bedeutet bei ausgeschaltetem Host-only: alle Spieler der erlaubten Spielmodi. Der Host darf die Whitelist umgehen. Item Giving verleiht keine Operatorrechte.

Death Kick: `death_kick=true`; `kick_delay_seconds` von 0 bis 60. Standard: **aus**. Ein Gast-Kick adressiert dessen aktuelle Verbindung. Der echte Essential-/Singleplayer-Ablauf ist noch manuell abzunehmen.

`store_conversations=false` deaktiviert Historie und verwirft geladene Gesprächseinträge. `/verity forget` löscht die eigene Historie. Lokale Datei: `<Weltordner>/verity/state.dat`. Keine Mikrofonaufnahmen oder Cloud-Übertragung.

## Offline-Mikrofon

Standardmäßig ausgeschaltet. Erforderlich sind SVC und ein rechtmäßig nutzbares Vosk-Modell auf dem **Host/Server**:

```text
config/verity/models/current/am/...
config/verity/models/current/conf/...
```

Die Modellwurzel muss direkt `current` sein, ohne zusätzlichen ZIP-Unterordner. Deutsch/Englisch benötigen das passende Modell; aktuell wird ein Modell pro Server geladen. Offizielle Modelle: https://alphacephei.com/vosk/models. Download und Bündelung konnten wegen HTTP 403 nicht abgeschlossen werden. Nach Installation Welt neu starten.

Jeder Sprecher aktiviert pro Verbindung freiwillig `/verity microphone enable`. Der Host erkennt dessen SVC-PTT-/Voice-Pakete offline. Nur Sätze mit „Verity“ am Anfang lösen Antworten aus. Spracheingaben werden derzeit privat beantwortet. Stop: `/verity microphone disable`. Reconnect deaktiviert die Verarbeitung erneut; ein roter HUD-Hinweis zeigt die Aktivierung an.

Maximal acht aktive Native-Recognizer, begrenzte Paketqueue, 15-Sekunden-Abschnitte. Bei Fehlern/Überlast wird die Eingabe abgeschaltet; Textchat bleibt nutzbar. Fehlende Modelle werden nicht durch vorgetäuschte Erkennung ersetzt.

## Befehle

```text
/verity help
/verity ask <Text>
/verity private <Text>
/verity follow
/verity stop
/verity mood
/verity phase
/verity settings
/verity forget
/verity voice test
/verity microphone enable|disable
```

Host/Operator: `/verity spawn`, `/verity remove`, `/verity mood set <0–100>`, `/verity book`, `/verity flashlight`. Entfernen benötigt eine geladene Entity. Bei ungeladenem Chunk wird kein Ersatz erzeugt. `/verity reset` und eine spielbare Phasensteuerung fehlen.

## Validierung und Grenzen

Siehe [Testplan](docs/TESTING.md), [Testbericht](docs/TEST_REPORT.md), [Drittkomponenten](THIRD_PARTY_NOTICES.md) und vollständige ursprüngliche Anforderung in `docs/REQUEST.txt`. Automatische Tests beweisen keine Mikrofon-, Grafik-, Essential- oder Zwei-Client-Funktion. Spieler-NBT und VERITY-Datei besitzen keine gemeinsame atomare Transaktion; eine harte Crash-Garantie ist nicht nachgewiesen.
