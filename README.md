# VERITY V5 – THE AI THAT WATCHES

**0.2.0-dev · Minecraft Java 26.3 · Fabric · Java 25**

VERITY beginnt jetzt in einer echten, versiegelten Box. Stelle sie ab, öffne sie mit Rechtsklick und beobachte, wie das Siegel bricht, der Deckel aufgeht und dein Begleiter erwacht. Du brauchst dafür keinen Spawn-Befehl.

**[Kompilierte Mod herunterladen](downloads/VerityV5-26.3-Fabric-dev.jar)** · [Installation für NoRisk und andere Launcher](docs/INSTALLATION.md) · [Prüfsumme](downloads/SHA256SUMS.txt) · [Änderungen](docs/CHANGELOG.md)

## Spielen

1. Minecraft schließen und die alte VERITY-JAR im `mods`-Ordner deines **NoRisk-Profils für 26.3/Fabric** durch diese ersetzen. Fabric API muss installiert sein. Nur eine VERITY-Version gleichzeitig verwenden.
2. Eine Welt öffnen. Beim ersten Beitritt gibt es das Warnbuch und eine Taschenlampe; die erste Person erhält außerdem die versiegelte Box. Volle Inventare verzögern die Zustellung, statt Gegenstände zu verlieren.
3. Die Box aufstellen, zwei Blöcke darüber frei lassen und rechtsklicken. Nach drei Sekunden erwacht ein gemeinsamer VERITY für die Welt, mit eigener Begrüßung. Eine geschlossene Box lässt sich mit Schleichen + Rechtsklick wieder aufnehmen.
4. `Verity, hallo!` im Chat schreiben. Er folgt dem Öffner entsprechend der Host-Regeln. `Verity, bleib hier` stoppt ihn. Right Shift öffnet die lokalen Einstellungen.
5. Die Taschenlampe mit Rechtsklick einschalten. Für ihren **sichtbaren Lichtkegel** zusätzlich [LambDynamicLights 4.13.0+26.3](https://github.com/LambdAurora/LambDynamicLights/releases/tag/v4.13.0%2B26.3) installieren. Den offiziellen Mod-Download separat in denselben `mods`-Ordner legen.

**Vorhandene Welten:** Erinnerungen, Vertrauen und der bereits gespawnte Begleiter bleiben erhalten. Ist VERITY bereits frei, kann der Host/Operator ihn mit `/verity pack` einpacken und die neue Begegnung erleben. Fehlt die erste Box, hilft `/verity box`. Für das Update keine Welt- oder Spielerdaten löschen. Der Fehler „Ungültige Spielerdaten“ aus 0.1.0 bleibt behoben.

Fabric 26.3 bleibt das Ziel, weil es zu deinem NoRisk-Profil passt und auch die Beleuchtungsintegration dafür verfügbar ist. Ein anderer Loader ist für die Box, Verwandlung oder Ereignisse nicht nötig.

## Das Abenteuer

- **Box und Wiedersehen:** eigener Block mit Holz-/Metallmodell, animiertem Scharnier, Siegel, Partikeln und Klang. Gleichzeitige Klicks erzeugen keinen zweiten Begleiter. Einpacken bewahrt UUID, Erinnerungen, Vertrauen und Story-Fortschritt.
- **Goldener Begleiter:** eigenes Kugelmodell, schwebende Bewegung, Blickkontakt, Blinzeln und Stimmungsgesicht. Serverseitige Flugnavigation mit Follow/Stay.
- **Persönlichkeit:** globale Stimmung, persönliches Vertrauen pro Spieler und getrennte Gesprächshistorien. Höflichkeit, Beleidigungen, Angriffe und „ja/nein“ beeinflussen seine Reaktion.
- **Entwicklung:** nach fünf aktiven Minuten wird er zum Beobachter. Nach zehn Minuten und Stimmung unter 45 wird er korrumpiert; nach fünfzehn Minuten und Stimmung unter 20 zum Dämon. Gute Behandlung kann diese Eskalation verhindern. Nur Spielzeit mit einem anwesenden Spieler und geladenem Begleiter zählt.
- **20 Ereignisvarianten:** Verschwinden, Auftauchen hinter dir, rote Augen in der Ferne, Schritte, Flüstern, Taschenlampenflackern, Seelenspur, Stimmungseinbruch, Gesichtsstörung, Dunkelheit, Nachricht, ferner Donner, Höhlenecho, Beobachter, Zittern, kalter Atem, Rauchspirale, Herzschlag, Warnstimme und Warntext. Standardabstand: 90 aktive Sekunden ab der Beobachterphase; keine Blockzerstörung.
- **Dämon:** dunkle Gestalt, rote Augen, wachsende Hörner und Klauenarme, Rauch während der Verwandlung, Verfolgung und Nahkampfschaden. Eine eingeschaltete, auf ihn gerichtete Taschenlampe hält ihn auf und verbessert Stimmung und persönliches Vertrauen. Bei Stimmung 80 beruhigt er sich wieder.
- **Eigene Stimme:** sieben mitgelieferte deutsche Sprachclips für Erwachen und Story, räumlich über Minecraft-Audio, mit Mundbewegung aus den tatsächlichen Audiopegeln. Es ist eine eigens erzeugte synthetische Stimme. Normale Chatantworten verwenden weiterhin die lokale OS-Stimme.
- **Taschenlampe:** physischer ON/OFF-Zustand, eigenes Modell und optionaler beweglicher Lichtkegel mit Entfernung, weichen Rändern und Hindernisprüfung. Dafür ist LambDynamicLights erforderlich; die Dämonenabwehr funktioniert auch ohne dessen Darstellung.

Das ist eine spielbare Weiterentwicklung, weiterhin eine Entwicklungsversion. Die vollständige ursprüngliche V5-Anforderung ist nicht vollständig umgesetzt: freie generative Gespräche, Originalstimmenimport, räumliche Ausgabe beliebiger Chatantworten, deren Audio-Lippensynchronisation, eigener Death Screen und das vollständige Einstellungsmenü fehlen. Die Dialoge verwenden eine begrenzte lokale Regelengine. Die Videoreferenz [f6f3PhauXyg](https://youtu.be/f6f3PhauXyg) war aus der Cloud nicht abrufbar; ein Vergleich mit dem Originalvideo wurde deshalb nicht durchgeführt.

## Weitere Funktionen und Regeln

Buch und Taschenlampe werden pro Spieler vergeben, eine neue Taschenlampe erst nach einem tatsächlichen Todes-Respawn. Item Giving ist standardmäßig aus und kann nach Host, UUID-Whitelist, Spielmodus, Menge, Höflichkeit und Cooldown freigeschaltet werden. Private Erinnerungen werden nicht an andere Clients übertragen. Speicherung erfolgt versioniert und mit Prüfsumme pro Welt unter `<Weltordner>/verity/state.dat`; alte Dateien werden übernommen. Bei Speicherfehlern wird kein leerer Zustand darübergeschrieben.

Beim Welt-/Serverstart entsteht `config/verity-server.properties`. Änderungen gelten nach Neustart:

```properties
story_enabled=true
story_chapter_seconds=300
horror_interval_seconds=90
demon_damage=true
death_kick=false
item_giving=false
```

`story_chapter_seconds` steuert die drei Zeitgrenzen; `demon_damage=false` schaltet den tatsächlichen Nahkampfschaden aus. `death_kick=true` aktiviert den optionalen verzögerten Kick nach Speicherung. Weitere Host-Optionen stehen in der erzeugten Datei. Auf Dedicated Servern setzt `host_uuid` den Host, im Singleplayer gilt der echte Weltbesitzer. Chatbehauptungen verleihen keine Rechte.

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

Host/Operator: `/verity box`, `/verity pack`, `/verity book`, `/verity flashlight`, `/verity mood set <0–100>`. `/verity spawn` und `/verity remove` bleiben Diagnosebefehle; der normale Einstieg erfolgt über die Box. Ein ungeladener Begleiter wird nicht durch einen neuen ersetzt. `/verity forget` löscht die eigene Historie; `store_conversations=false` verhindert deren Aufbewahrung.

## Optionale Mikrofoneingabe und Multiplayer

Mikrofoneingabe ist standardmäßig aus. Erforderlich sind eine passende **Simple Voice Chat-Mod** und ein Vosk-Sprachmodell auf dem Host/Server. Vosk/JNA sind enthalten, das Sprachmodell nicht. Offizielle Modelle: https://alphacephei.com/vosk/models. Die Modellwurzel muss direkt unter `config/verity/models/current/` liegen, mit `am/` und `conf/`. Nach der Installation neu starten.

Jeder Sprecher aktiviert die Verarbeitung pro Verbindung mit `/verity microphone enable`. Nur Äußerungen mit „Verity“ am Anfang lösen Antworten aus. Verarbeitung erfolgt lokal über SVC/Opus/Vosk; Antworten sind derzeit privat. `/verity microphone disable` oder Reconnect beendet die Zustimmung. Es werden keine Mikrofonaufnahmen gespeichert. Ohne eingerichtetes Modell bleibt Textchat nutzbar.

Alle Spieler benötigen VERITY und passende Fabric-Abhängigkeiten. Essential wird nicht mitgeliefert. Die Mod verwendet die vorhandene Host-Welt; Essential-Einladungen, NoRisk-Grafikstart, echte Mikrofone und zwei verbundene Clients wurden hier nicht live geprüft.

## Bauen und prüfen

Java-25-JDK installieren und `JAVA_HOME` darauf setzen:

```bat
gradlew.bat clean build
```

Linux/macOS: `./gradlew clean build`. In der vorbereiteten Cloud:

```bash
python3 scripts/cloud_setup.py
python3 scripts/cloud_gradle.py clean build --no-daemon
```

Der Build führt JUnit-Kerntests und einen echten Minecraft/Fabric-GameTest-Server aus. Er lädt die offizielle LambDynamicLights-Veröffentlichung mit gepinnter SHA-256 ausschließlich zur Extraktion der Compile-API; weder diese Mod noch deren API werden in VERITY gebündelt. Ergebnis: `build/libs/VerityV5-26.3-Fabric-dev.jar`.

Siehe [Testbericht](docs/TEST_REPORT.md), [Testplan](docs/TESTING.md), [Drittkomponenten](THIRD_PARTY_NOTICES.md) und [ursprüngliche Anforderung](docs/REQUEST.txt). Automatische Servertests ersetzen keine Sicht-/Hörprüfung im grafischen Client.
