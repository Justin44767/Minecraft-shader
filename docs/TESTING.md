# Testplan und Ergebnisgrenzen

Der Build führt die JUnit-Kerntests und den echten Fabric-GameTest-Server für Minecraft 26.3 aus. Die fünf projektbezogenen GameTests kontrollieren Buchinhalt, Taschenlampen-Datenkomponente, Entity-Attribute/Serverzustand, künstliche Befehlsquellen sowie Operator-Beitritt einschließlich Befehlsübertragung, Starter-Inventar und abgewiesener Gastrechte. Der Beitritt verwendet eine Netty-EmbeddedChannel-Verbindung und Minecrafts echten `PlayerList.placeNewPlayer`-Pfad. Der Fabric-Runner fügt einen eigenen Kontrolltest hinzu. Das ersetzt keinen grafischen NoRisk- oder Zwei-Client-Test.

```bash
./gradlew clean build
# Oder in dieser Cloud:
python3 scripts/cloud_setup.py
python3 scripts/cloud_gradle.py clean build --no-daemon
```

Unter Windows: `gradlew.bat clean build` mit einem Java-25-JDK. Nur Kerntests: `./gradlew -p core clean test`.

Aktuelle maschinenlesbare Ergebnisse:

- `core/build/test-results/test/TEST-verity.core.VerityCoreTest.xml`
- `build/reports/gametest/results.xml`
- `build/run/gameTest/logs/latest.log`

Die Kerntests decken ab: UUID statt Namensbehauptung, getrenntes Vertrauen und private Erinnerungen, nachträglicher Berechtigungsentzug, Item-Menge/Whitelist/Cooldown, Queue-Limits und gleichzeitige Sprecher, Verbindungswechsel, Timeouts und Wiederholungen, Starter- und Respawn-Tickets, verspätete Zustellung, persistierte Anfragen, beschädigte Speicherdateien, Hintergrundspeicherung und Gruppen-Abstimmungslogik.

## Noch erforderliche manuelle Abnahme

Diese Prüfungen wurden nicht durchgeführt und dürfen nicht als bestanden gelten:

1. Launcher-/Grafikstart auf Windows und Linux, Ressourcenladen, vollständige Sichtbarkeit aller Buchseiten, Taschenlampen- und Entity-Modell, Augenanimation, Mood-HUD, kleine GUI-Auflösungen und Right-Shift-Menü.
2. Reale TTS-Ausgabe mit installierter OS-Stimme; aufeinanderfolgende Antworten ohne Verdopplung, Disconnect und Ausschalten der Stimme. Audioqualität, Sprache und native OS-Bibliotheken sind vom Zielsystem abhängig.
3. Deutsch-/englischsprachige Vosk-Modelle, echte Mikrofonpakete über SVC, PTT, Satzanfang-Wake-Word, Ein-/Ausschalten der Zustimmung, Überlast, Leave und Reconnect. Kein Sprachmodell installiert: der offizielle Download wurde mit HTTP 403 abgewiesen.
4. Zwei echte Minecraft-Clients: gleichzeitige Chat- und Voice-Eingaben, öffentliche Nähe-Antworten, private Audio-/Text-Ausgabe, Inventarüberlauf und richtige Item-Empfänger. Unit-Tests mit Threads ersetzen diesen Test nicht.
5. Essential: Einladung, Beitritt, integrierter Server, Late Join und Gasttod ohne Schließen der Host-Welt. Essential wurde nicht installiert; Kompatibilität ist nicht bestätigt.
6. Tatsächlicher Tod, Totem-Rettung, KeepInventory, Death-Kick-Delay, Reconnect im Todeszustand und genau eine Belohnung nach Respawn. Die Kern-Zustandsmaschine ist geprüft; kein menschlicher Spieler wurde getrennt.
7. Harte Prozessabbrüche während Inventar-/Welt-Speicherung. Spieler-NBT und VERITY-Datei sind keine gemeinsame atomare Transaktion. Zustellmarker und Speicherreihenfolge reduzieren das Wiederholungsfenster, sind aber keine bewiesene Crash-Garantie.

Die Dateiprüfsumme erkennt unbeabsichtigte Beschädigung. Sie verschlüsselt oder authentifiziert lokal gespeicherte Gespräche nicht. Der Serveradministrator hat Zugriff auf den Weltordner. Private Inhalte werden nicht an andere Clients synchronisiert.
