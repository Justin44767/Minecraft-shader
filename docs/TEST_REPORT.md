# VERITY 0.2.0-dev – Testbericht

Stand: 27. Oktober 2026. Minecraft Java 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Loom 1.19.3, Gradle 9.8.2, Java 25. Linux x86_64.

**Die Box-Begegnung und das Verhalten der Abenteuerfassung sind im Minecraft-Testserver geprüft. Grafischer NoRisk-Start, Darstellung und Live-Audio sind noch manuell abzunehmen.**

## Ergebnisse

| Prüfung | Ergebnis |
|---|---|
| Cloud-Einrichtung | Java-25-JDK und Wrapper vorbereitet, offizielle Downloads mit SHA-256 geprüft; Proxy und System-Truststore nutzbar |
| Build | `python3 scripts/cloud_gradle.py clean build --no-daemon` sowie abschließendes `build` erfolgreich |
| JUnit-Kernlogik | **53 Tests, 0 Fehler, 0 übersprungen**: 46 Kernfälle und 7 Abenteuer-/Lichtkegel-Fälle |
| Minecraft/Fabric | **6 VERITY-GameTests erfolgreich**, zusätzlich 1 Fabric-Kontrolltest |
| Reale Abenteuersequenz | Box-Block und BlockEntity, 60-Tick-Öffnung, Doppelklickschutz, genau eine Entity, Einpacken und erneut dieselbe UUID |
| Ereignisse und Dämon | Alle 20 Ereignisse serverseitig aufgerufen; Dunkelheit, Verstecken und Glitch geprüft; Verwandlung und echter Survival-Schaden sowie Taschenlampenabwehr/Stimmungsgewinn geprüft |
| Berechtigungsregression | Synthetische Quellen ohne Server, Operator-Login und verweigerte Gastrechte bestehen weiterhin |
| Ressourcen/Paket | JSON, PNG, Root-/Nested-JAR-CRC und Audio-Dekodierung geprüft; Java-Classfile-Version 69. Keine LambDynamicLights-Datei oder deren API gebündelt |
| Audio | Sieben eigene deutsche Mono-Vorbis-Clips; Mundkurven aus RMS-Werten der final dekodierten OGGs. Keine Live-Hörprobe |

Die JUnit-Fälle für altes Speicherformat, unterbrochene Öffnung und Rückverpackung prüfen die Kernlogik. Der GameTest verwendet reale Serverblöcke und Entities sowie einen Spieler über Netty EmbeddedChannel. Das ist kein grafisch verbundener Minecraft-Client. Im Kampftest werden Testfiguren in Reichweite gehalten; Gelände-Wegfindung wird damit nicht behauptet.

## Behobener Beitrittsfehler

0.1.0-dev scheiterte beim NoRisk-Weltbeitritt: Minecrafts künstliche Berechtigungsquelle enthielt keinen Server. Ein Nullschlüssel beim Runtime-Lookup führte über `Commands.sendCommands` zu „Ungültige Spielerdaten“. Die Korrektur aus 0.1.1 ist enthalten und wird weiterhin über den echten Login-Pfad geprüft. Welt-/Spielerdaten müssen für dieses Update nicht gelöscht werden.

## Migration und Integrationen

Das Speicherformat ist jetzt Version 2. Version 1 wird gelesen: Profile, Vertrauen, Erinnerungen, Inventaransprüche und eine vorhandene Entity bleiben erhalten. Ein bestehender Begleiter überspringt die erste Box; der Host kann `/verity pack` nutzen. Eine Welt ohne Begleiter erhält eine Box ohne erneute Starter-Geschenke.

Die Lichtkegel-Integration kompiliert gegen die echte LambDynamicLights-API aus der offiziellen Veröffentlichung 4.13.0+26.3. SHA-256 des nur zum Kompilieren geladenen Bundles: `b7550288423affed86370acd80d064876d7e256f5489461b7ff7941627552ab2`. Die Zusatzmod ist separat zu installieren und nicht in VERITY enthalten. Das Beleuchtungsbild wurde noch nicht geprüft.

Die eigenen Storyclips sind eingebaut und benötigen weder SVC noch OS-Stimme. Beliebige Chatantworten verwenden weiterhin die separate OS-Narrator-Instanz und sind nicht räumlich oder audio-lippensynchron. Vosk/SVC-Eingabe benötigt ein separat eingerichtetes Sprachmodell; dessen offizieller Download war aus der Cloud mit HTTP 403 blockiert.

## Verbleibende Grenzen

Kein grafischer NoRisk-Test, kein Essential-Invite, kein Zwei-Client-/UDP-Test und keine echten Mikrofone. Ebenfalls noch offen: Originalvideo-Vergleich (YouTube HTTP 403), generative KI, Originalstimmenimport, eigener Death Screen und vollständiges Kategorienmenü. Harte Crash-Sicherheit über Minecraft-NBT und VERITY-Datei hinweg ist nicht bewiesen. Details: [manueller Testplan](TESTING.md).

Die Umgebungseinrichtung wurde mit dem Skill `cloud-environment-onboarding:setup` vorbereitet; der vorhandene Konfigurationsentwurf enthält Setup-/Buildbefehle und Netzwerkdomains. Er wird durch diese Veröffentlichung nicht automatisch zu einer veröffentlichten Umgebungskonfiguration.
