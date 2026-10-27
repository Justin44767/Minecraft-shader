# Änderungen

## 0.2.0-dev

Der Einstieg erfolgt über eine echte versiegelte Box statt über einen Spawn-Befehl. Der erste Spieler erhält das Paket automatisch. Abstellen und Rechtsklick starten die dreisekündige Öffnung mit beweglichem Deckel, Partikeln, Geräuschen und VERITYs eigener Begrüßung.

- Weltweit gemeinsamer Begleiter; konkurrierende Klicks und leere Boxen erzeugen keinen zweiten VERITY. `/verity pack` bewahrt Identität, Erinnerungen, Stimmung und Story-Fortschritt, `/verity box` ersetzt eine fehlende versiegelte Box.
- Automatische Freund-/Beobachter-/Korruptions-/Dämonenentwicklung nach aktiver Spielzeit und Stimmung; 20 nicht blockzerstörende Ereignisse. Zeiten und tatsächlicher Dämonenschaden sind konfigurierbar.
- Neue dunkle Darstellung, Hörner und Klauen, Rauchverwandlung, Verfolgung und Nahkampf. Eine gezielte Taschenlampe verhindert Angriffe und hilft beim Beruhigen.
- Sieben eigene deutsche räumliche Storyclips; Mundbewegung aus Audiopegeln der finalen Dateien. Beliebige Chatantworten verwenden weiterhin die OS-Stimme.
- Optionaler Taschenlampenkegel über die echte LambDynamicLights-API mit Richtung, Entfernung, weichen Rändern und Hindernisprüfung. Offizielle Zusatzmod für 26.3 separat installieren; keine fremde Beleuchtungs-JAR gebündelt.
- Speicherformat 2 mit Übernahme des alten Formats; bestehende Begleiter und Profile bleiben erhalten. Neue Welten bekommen die Box, bestehende Begleiter können eingepackt werden.
- 53 Kerntests und 6 VERITY-GameTests, darunter reale Box-Sequenz, Ereignisaufrufe, Dämonenschaden und Taschenlampenabwehr. Der Beitrittsfix aus 0.1.1 bleibt geprüft. Minecraft 26.3/Fabric bleibt für das vorhandene NoRisk-Profil erhalten.

Grafik, Audioqualität, NoRisk-Livebetrieb, Essential und zwei echte Clients sind noch manuell abzunehmen. Vollständige generative KI, Originalstimmenimport und die übrigen offenen Anforderungen sind in README/Testplan benannt.

## 0.1.1-dev

Behebt „Ungültige Spielerdaten“ beim Weltbeitritt, gemeldet mit NoRisk Client, Minecraft 26.3 und Fabric Loader 0.20.1.

Minecraft ruft beim Erzeugen des clientseitigen Befehlsbaums Berechtigungsprädikate mit einer künstlichen Kompilierungsquelle auf. Diese Quelle hat keinen Server und keinen Spieler. VERITY hat zuvor versucht, den Server aus dieser Quelle in einer `ConcurrentHashMap` nachzuschlagen. Der Nullschlüssel führte zu einer `NullPointerException` während `Commands.sendCommands`, worauf Minecraft den Beitritt abbrach.

- Künstliche Quellen werden vor jedem Zugriff auf den Serverzustand als nicht administrativ behandelt.
- Ein Runtime-Lookup ohne Server liefert keinen Zustand zurück.
- Berechtigungen authentifizierter Hosts/Operatoren bleiben erhalten; Gäste bekommen keine administrativen Rechte.
- Das Welt-Speicherformat, Minecraft-Version und Loader bleiben unverändert.

Regression: Vor der Korrektur scheiterten zwei neue GameTests mit demselben Nullzugriff, darunter ein Operator-Beitritt über Minecrafts tatsächlichen Login-Pfad. Nach der Korrektur bestehen sie, einschließlich Zustellung von genau einem Buch und einer Taschenlampe. Ein grafischer NoRisk-Client wurde in der Cloud nicht gestartet.

**Installation des Updates:** Minecraft schließen, im betroffenen Profil die bisherige VERITY-JAR durch 0.1.1-dev ersetzen und neu starten. Nicht beide VERITY-Versionen parallel installieren. Für diese Korrektur keine Welt- oder Spielerdaten löschen.
