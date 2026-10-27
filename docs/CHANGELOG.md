# Änderungen

## 0.1.1-dev

Behebt „Ungültige Spielerdaten“ beim Weltbeitritt, gemeldet mit NoRisk Client, Minecraft 26.3 und Fabric Loader 0.20.1.

Minecraft ruft beim Erzeugen des clientseitigen Befehlsbaums Berechtigungsprädikate mit einer künstlichen Kompilierungsquelle auf. Diese Quelle hat keinen Server und keinen Spieler. VERITY hat zuvor versucht, den Server aus dieser Quelle in einer `ConcurrentHashMap` nachzuschlagen. Der Nullschlüssel führte zu einer `NullPointerException` während `Commands.sendCommands`, worauf Minecraft den Beitritt abbrach.

- Künstliche Quellen werden vor jedem Zugriff auf den Serverzustand als nicht administrativ behandelt.
- Ein Runtime-Lookup ohne Server liefert keinen Zustand zurück.
- Berechtigungen authentifizierter Hosts/Operatoren bleiben erhalten; Gäste bekommen keine administrativen Rechte.
- Das Welt-Speicherformat, Minecraft-Version und Loader bleiben unverändert.

Regression: Vor der Korrektur scheiterten zwei neue GameTests mit demselben Nullzugriff, darunter ein Operator-Beitritt über Minecrafts tatsächlichen Login-Pfad. Nach der Korrektur bestehen sie, einschließlich Zustellung von genau einem Buch und einer Taschenlampe. Ein grafischer NoRisk-Client wurde in der Cloud nicht gestartet.

**Installation des Updates:** Minecraft schließen, im betroffenen Profil die bisherige VERITY-JAR durch 0.1.1-dev ersetzen und neu starten. Nicht beide VERITY-Versionen parallel installieren. Für diese Korrektur keine Welt- oder Spielerdaten löschen.
