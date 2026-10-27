# VERITY V5 – Testbericht

Stand: 27. Oktober 2026. Entwicklungsstand `0.1.0-dev`, Minecraft Java 26.3, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Loom 1.19.3, Gradle 9.8.2, Java 25. Linux x86_64.

**Ergebnis: kompilierbare und teilweise automatisch geprüfte Entwicklungsbasis. Die vollständige angeforderte Horror-Mod ist nicht fertig.**

## Ausgeführt

| Prüfung | Tatsächliches Ergebnis |
|---|---|
| `python3 scripts/cloud_setup.py` | Erfolgreich: vorbereitetes Java-25-JDK und Proxy-/System-Truststore-Konfiguration verwendbar. JDK-Download wurde vor der Extraktion gegen die offizielle SHA-256 geprüft. Der erneute Helfer-Aufruf verwendet den vorhandenen JDK-Ordner |
| `python3 scripts/cloud_gradle.py clean build --no-daemon` | Erfolgreich ausgeführt, einschließlich Compile Main/Client, JAR, Quellen und Tests |
| Abschließendes `python3 scripts/cloud_gradle.py build --no-daemon` | Erfolgreich nach den letzten Änderungen; 29 Sekunden, 15 Tasks |
| JUnit-Kerntests | **39 ausgeführt, 39 bestanden, 0 fehlgeschlagen, 0 übersprungen** |
| Minecraft/Fabric-GameTests | **3 VERITY-Tests bestanden**: zehn echte Buchseiten mit Autor, gespeicherter Schalterzustand bei Item-Kopie, Entity-Factory/Attribute und Serverzustand |
| Fabric-Runner-Kontrolltest | `minecraft:always_pass` bestanden. Der Runner meldet daher insgesamt 4 Tests. Dieser Kontrolltest zählt nicht als VERITY-Funktionsnachweis |
| Paketprüfung | Root-JAR und alle drei verschachtelten JARs bestehen die ZIP-CRC-Prüfung. Main-, Client- und SVC-Entrypoint-Klassen sind vorhanden, Java-Classfile-Version 69 (= Java 25) |
| Ressourcen | JSON-Dateien parsebar; zehn nichtleere Buchseiten; eigene PNGs, Item-Geometrie und Java-Entity-Geometrie vorhanden |
| Repository | `git diff --check` ohne Fehler; dieser Bericht dokumentiert den lokalen Entwicklungs-Build, keinen fertigen GitHub-Release |

Die JUnit-Fälle prüfen unter anderem acht gleichzeitig einreichende Sprecher, UUID-/Empfängerbindung, Rechteentzug vor Ausführung, Namenstäuschung, fremde Item-Empfänger, Whitelists, Mengenlimits, unklare Mengen, Cooldown, Sessionwechsel, private Erinnerungen, History-Limit/Löschen, Reconnect ohne Starter-Duplikat, Todesanspruch bis Respawn, isolierten Kick-Plan, Dateibeschädigung und koaleszierte Speicherung inklusive simulierter Schreibfehler. Das sind Tests der Kernlogik, keine Tests mit acht echten Mikrofonen oder Clients.

## Diagnostizierte Einschränkungen

- Der Vosk-Modell-Download von `alphacephei.com` wurde durch den Proxy mit **HTTP 403 Forbidden** abgewiesen. Kein Modell installiert oder gebündelt. Die Java-/SVC-Anbindung kompiliert; echte Erkennung wurde nicht ausgeführt. Die Domain wurde dem Konfigurationsentwurf hinzugefügt, der Entwurf allein ändert noch nicht die laufende Netzfreigabe.
- Der GameTest-Server meldet nicht verfügbare Minecraft-Online-Services. Er führt seine lokalen Tests dennoch vollständig aus. Es wurde keine erfolgreiche Konto-Anmeldung oder Online-Multiplayer-Verbindung behauptet.
- Die erste GameTest-Ausführung erzeugte vorläufige Serverdateien und meldete fehlende `server.properties`/`eula.txt`. Der spezielle Test-Runner startete und beendete seine Testwelt anschließend erfolgreich. Es wurde keine normale Dedicated-Server-EULA-Datei auf `true` gesetzt.
- Gradle meldet veraltete API-Nutzung im Fabric-Test-/Buildpfad. Der Build ist auf Gradle 9.8.2 festgelegt; Gradle 10 wurde nicht geprüft.
- In dieser Cloud wurden weder grafischer Minecraft-Client noch echte Audiogeräte verwendet. OS-TTS, Entity-/Item-Darstellung, GUI, Flugnavigation im Gelände und echte Item-/Death-Abläufe mit verbundenen Spielern bleiben manuell zu prüfen.
- Essential wurde nicht installiert. Kein Essential-Invite-, Host-/Gast-, Zwei-Client-, Voice-UDP- oder Live-Death-Kick-Test ausgeführt.

## Nicht umgesetzt

Keine fertige Horror-Kampagne, Story-Kiste, 20 Horror-Ereignisse, vollständige Demon-Form/-Transformation/-KI, räumliche SVC-Audioantworten, Audio-Lippensynchronisation, lizenzierter Originalstimmenimport, dynamischer Lichtkegel, eigener Death Screen oder vollständiges Kategorien-/Slider-Menü. Gruppen-Abstimmungslogik ist als Kernkomponente getestet, aber noch nicht an die Spiel-Story angeschlossen. Die Taschenlampe besitzt derzeit Modell, Zustand und Geräusch, **keine tatsächliche Beleuchtung**.

Das Artefakt heißt deshalb `VerityV5-26.3-Fabric-dev.jar`. Es wird nicht als fertige `VerityV5-26.3-Fabric.jar` ausgegeben. Quellen, reproduzierbare Buildbefehle und [verbleibender Testplan](TESTING.md) sind beigefügt.

## Umgebungsentwurf

Gespeichert wurden `install_script`, `start_skill` und ergänzende Netzwerkdomains für Fabric/Minecraft, optionale Mod-Repositories und Vosk. Kein Secret oder Konto erforderlich bzw. angefordert. Der Entwurf ist gespeichert, nicht veröffentlicht; Prüfung/Speichern in den Umgebungseinstellungen und Veröffentlichung erfolgen durch den Benutzer. Eine Wiederherstellung in einer frischen Task wurde nicht getestet.
