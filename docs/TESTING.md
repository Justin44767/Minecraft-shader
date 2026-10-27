# Tests und manuelle Abnahme

Der Build führt **53 JUnit-Kerntests** und einen echten **Fabric-GameTest-Server für Minecraft 26.3** aus. Sechs Tests stammen aus VERITY; Fabric ergänzt `minecraft:always_pass` als Kontrolltest. Der Build scheitert bei einem erforderlichen Testfehler.

```bash
./gradlew clean build
# Vorbereitete Cloud:
python3 scripts/cloud_setup.py
python3 scripts/cloud_gradle.py clean build --no-daemon
```

Windows: `gradlew.bat clean build` mit Java 25. Nur Kernlogik: `./gradlew -p core test`.

Ergebnisse:

- `core/build/test-results/test/TEST-verity.core.VerityCoreTest.xml`
- `core/build/test-results/test/TEST-verity.core.VerityAdventureTest.xml`
- `build/reports/gametest/results.xml`
- `build/run/gameTest/logs/latest.log`

Der Runner entfernt vor dem Start ausschließlich `build/run/gameTest/world`, seine erzeugte Testwelt. So beeinflussen alte Test-Spielstände nicht die Box-Autorität. Eine Benutzerwelt oder ein Launcher-Profil wird dabei nicht geöffnet oder gelöscht.

## Automatisch abgedeckt

- UUID-/Sitzungsbindung, getrennte Erinnerungen und Vertrauen, Rechteentzug vor Ausführung, Whitelists, Mengen, Cooldowns, Queue-Limits, parallele Sprecher, Timeouts und Replay-Abweisung.
- Inventar-Tickets bei Reconnect, Tod und echtem Respawn; volle Inventare behalten ausstehende Zustellungen. Dateibeschädigung, atomarer Dateiersatz und simulierte Speicherfehler.
- Automatische erste Box, konkurrierende Öffner, persistierte halbe Auspacksequenz, Einpacken mit gleicher Identität und Erinnerung, Ersatz nach zerstörter Box sowie Migration einer echten Datei des alten Formats ohne erneute Starter-Geschenke.
- Story-Uhr, Zeit-/Stimmungsschwellen, Beruhigung und nur dem Helfer zugeordnetes Vertrauen. Richtungsabhängiger Lichtkegel, weiche Ränder, Entfernung und Oberflächenabschattung als reine Mathematik.
- Minecrafts reale Befehlsübertragung während `PlayerList.placeNewPlayer` mit Netty EmbeddedChannel: Operator kann beitreten, Buch/Lampe werden zugestellt, Gäste erhalten keine administrativen Rechte. Künstliche Quellen ohne Server bleiben ungefährlich.
- Realer Box-Block mit BlockEntity, 60-Tick-Öffnung, genau ein Begleiter, Doppelklickschutz, Einpacken und erneutes Auspacken derselben UUID. Der Test ruft alle 20 Ereignisse im Server auf und prüft Dunkelheit, Unsichtbarkeit und Gesichtsstörung. Anschließend prüft er Verwandlung, Ablauf der Unsichtbarkeit, tatsächlichen Schaden an einem Survival-Spieler sowie ausbleibenden Schaden und Stimmungsgewinn bei gezielt eingeschalteter Taschenlampe.

Der Box-Test hält die Testfiguren für die Nahkampfprüfung in Reichweite. Er beweist deshalb die Entscheidung über Angriff/Abwehr, keine zuverlässige Wegfindung durch beliebiges Gelände. Partikel-, Sound- und Titelaufrufe werden serverseitig ausgeführt; ihre grafische bzw. hörbare Wirkung wird dadurch nicht beurteilt.

## Manuell noch prüfen

1. NoRisk/Windows-Grafikstart mit der neuen JAR: Box aufstellen, Deckel, Siegel, Erwachen, Kugelgesicht und Dämonengestalt; alle Buchseiten und kleine GUI-Auflösungen.
2. Sieben Storyclips hören: räumliche Richtung, Lautstärke, Mundbewegung und Unterbrechung beim Einpacken. Danach OS-TTS mit `/verity voice test` und Chatantworten prüfen. Die generierten OGGs werden technisch dekodiert geprüft, eine menschliche Hörprobe erfolgte nicht.
3. Offizielles LambDynamicLights 4.13.0+26.3 installieren: Lampe in Höhle an-/ausschalten, drehen, vor Wänden und mit mehreren Spielern prüfen. Ohne Zusatzmod darf die Box weiter funktionieren. Die Integration kompiliert gegen deren echte API; die Client-Beleuchtung ist noch nicht visuell abgenommen.
4. Zwei reale Clients: gleichzeitige Chat-/Voice-Eingaben, private Antworten, gemeinsames Auspacken, Late Join, Einpacken, Reconnect und voller Inventarplatz. Essential-Einladungen und NoRisk-Modkombinationen gesondert prüfen.
5. Simple Voice Chat mit passendem Vosk-Modell: echte Mikrofone, PTT, Wake-Word, Einwilligung, Überlast und Reconnect. Kein Modell wurde gebündelt oder live geprüft.
6. Echter Spielertod, Totem, KeepInventory, optionaler Death Kick und korrekte Belohnung nach Respawn. Keine lebende Spieler-Verbindung wurde im Test getrennt.
7. Kontrollierter Neustart während des Auspackens sowie harte Prozessabbrüche bei Welt-/Inventarspeicherung. Die Kern-Datei übernimmt den Fortschritt; Block-/Entity-NBT und VERITY-Datei besitzen jedoch keine gemeinsame atomare Transaktion. Eine vollständige Crash-Garantie ist nicht nachgewiesen.

Die Prüfsumme erkennt Dateibeschädigung, verschlüsselt Gesprächsdaten aber nicht. Serveradministratoren können den Weltordner lesen. Private Inhalte werden nicht an andere Clients synchronisiert.
