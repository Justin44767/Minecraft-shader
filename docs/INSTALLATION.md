# VERITY 0.2.0-dev installieren

Die Datei `VerityV5-26.3-Fabric-dev.jar` ist eine Fabric-Mod für **Minecraft Java 26.3**, **Java 25**, **Fabric Loader ab 0.20.1** und die zu 26.3 passende **Fabric API**.

## NoRisk Client

1. Minecraft vollständig schließen.
2. Im NoRisk Client dein Profil für **26.3 / Fabric** auswählen und dessen Spiel-/Profilordner öffnen. Verwende den `mods`-Ordner dieses Profils.
3. Die bisherige VERITY-JAR entfernen und [die neue kompilierte JAR](../downloads/VerityV5-26.3-Fabric-dev.jar) dort ablegen. Nicht mehrere VERITY-Versionen gleichzeitig laden. Fabric API muss vorhanden sein; dein gemeldetes Profil enthält bereits `0.165.0+26.3`.
4. Für den sichtbaren Taschenlampenkegel zusätzlich die offizielle [LambDynamicLights-JAR für 26.3](https://github.com/LambdAurora/LambDynamicLights/releases/tag/v4.13.0%2B26.3) herunterladen und im selben `mods`-Ordner ablegen. VERITY bündelt sie nicht.
5. Das Profil starten und eine Welt öffnen. Buch und Taschenlampe kommen automatisch; die erste Person bekommt eine Box. Freie Inventarplätze bereit halten.
6. Box abstellen, zwei Blöcke darüber frei lassen und rechtsklicken. Das Siegel öffnet sich, VERITY erscheint und begrüßt dich. Mit `Verity, hallo!` kannst du ihn ansprechen.

**Alte Welten bleiben nutzbar.** Ein vorhandener Begleiter bleibt mit seinen Erinnerungen erhalten. Als Host/Operator `/verity pack` verwenden, um ihn in die neue Box einzupacken. Bei fehlender erster Box `/verity box` ausführen. Bei ungeladenem Begleiter erst in seine Nähe gehen.

## Offizieller Minecraft Launcher

Minecraft Java 26.3 einmal starten und schließen. Mit dem offiziellen [Fabric Installer](https://fabricmc.net/use/installer/) Loader 0.20.1 für 26.3 installieren. Das Fabric-Profil mit Java 25 verwenden und dessen Spielverzeichnis öffnen. Unter Windows ist der Standard `%appdata%\.minecraft`; ein abweichendes Profilverzeichnis hat seinen eigenen `mods`-Ordner.

Dorthin kommen die JARs:

```text
Spielverzeichnis/
  mods/
    fabric-api-0.165.0+26.3.jar
    VerityV5-26.3-Fabric-dev.jar
    lambdynamiclights-4.13.0+26.3.jar
```

Die dritte Datei ist für die sichtbare Beleuchtung optional. Für Mikrofoneingabe werden zusätzlich Simple Voice Chat und ein eingerichtetes Offline-Sprachmodell benötigt, siehe [README](../README.md). Essential ist separat zu installieren; seine Live-Kompatibilität wurde nicht geprüft.

## Prism, Modrinth App und andere Launcher

Eine Minecraft-26.3-Instanz mit Fabric und Java 25 erstellen. VERITY und Fabric API über die Mod-Verwaltung hinzufügen oder in den `mods`-Ordner dieser Instanz legen. Für Beleuchtung LambDynamicLights ergänzen.

VERITY ist kein Modpack-Export, kein Fabric-Installer und keine Minecraft-Spiel-JAR. Die Datei als Mod hinzufügen. GitHubs „Source code / Download ZIP“ enthält den Quellcode; zum Spielen die kompilierte Datei aus `downloads` verwenden.

## „Ungültige Spielerdaten“

Der mit NoRisk gemeldete Fehler aus **0.1.0-dev** wurde in 0.1.1 behoben; diese Korrektur ist in 0.2.0 enthalten. Minecrafts künstliche Befehlsquelle hatte keinen Server und löste einen Nullzugriff in VERITY aus. Ein echter Spielerbeitritt im Fabric-Testserver prüft die Korrektur weiterhin.

**Nur die alte VERITY-JAR ersetzen. Keine Welt- oder Spielerdaten löschen.**

Falls es mit 0.2.0 erneut beim Weltbeitritt scheitert, den neuen Fehlerblock aus `logs/latest.log` des Profils prüfen. Bei Fabric-Versionsmeldungen Minecraft 26.3, Java 25, Loader und Fabric API abgleichen. Die [SHA-256-Datei](../downloads/SHA256SUMS.txt) identifiziert den aktuellen Download.
