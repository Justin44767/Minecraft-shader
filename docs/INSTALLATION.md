# Entwicklungs-JAR installieren

Die Datei `VerityV5-26.3-Fabric-dev.jar` ist eine **Fabric-Mod**. Sie wird in den `mods`-Ordner einer bestehenden Minecraft-Installation kopiert. Minecraft, Fabric Loader und Fabric API werden separat installiert.

## Minecraft Launcher

1. Im offiziellen Minecraft Launcher **Minecraft Java 26.3** einmal ohne Mods starten und wieder schließen. Dadurch werden die eigentlichen Spieldateien durch den Launcher installiert.
2. Den offiziellen [Fabric Installer](https://fabricmc.net/use/installer/) verwenden: Minecraft **26.3**, Fabric Loader **0.20.1** wählen und das Client-Profil installieren.
3. Im Minecraft Launcher das erzeugte **Fabric-Profil** auswählen. Für Minecraft 26.3 wird **Java 25** benötigt; bei einem selbst eingetragenen Java-Pfad muss dieser auf Java 25 zeigen.
4. Das Spielverzeichnis dieses Profils öffnen. Unter Windows ist der Standard `%appdata%\.minecraft`; ein im Launcher abweichend eingestelltes Spielverzeichnis hat seinen eigenen `mods`-Ordner.
5. Im Spielverzeichnis den Ordner `mods` anlegen, falls er fehlt. Darin **Fabric API 0.165.0+26.3** und die [VERITY-Entwicklungs-JAR](../downloads/VerityV5-26.3-Fabric-dev.jar) ablegen.
6. Das Fabric-Profil starten und zunächst eine Testwelt öffnen. Der Host kann `/verity spawn` verwenden.

Beispiel für den Inhalt des Spielverzeichnisses:

```text
Spielverzeichnis/
  mods/
    fabric-api-0.165.0+26.3.jar
    VerityV5-26.3-Fabric-dev.jar
```

Für den ersten Start werden VERITY und Fabric API benötigt. Simple Voice Chat und Essential können danach in einer zu Minecraft 26.3 und Fabric passenden Version ergänzt werden; ihre Live-Kompatibilität ist noch nicht getestet. Die Entwicklungsversion und die fehlenden Funktionen sind in der [README](../README.md) dokumentiert.

## Andere Launcher

In Prism, Modrinth App oder CurseForge eine **Minecraft-26.3-Instanz mit Fabric** erstellen. Die beiden Mod-JARs im `mods`-Ordner dieser Instanz ablegen bzw. über deren Mod-Verwaltung hinzufügen.

Die VERITY-JAR ist kein Modpack-Export, kein Fabric-Installer und keine Minecraft-Spiel-JAR. Daher gehört sie weder in einen Modpack-Import noch als benutzerdefinierte Spielversion in den Launcher. Der GitHub-Download „Source code / Download ZIP“ enthält den Quellcode und wird ebenfalls nicht als Mod installiert.

## „Ungültige Spielerdaten“ beim Weltbeitritt (0.1.0-dev)

Für 0.1.0-dev ist ein Fehler in der VERITY-Berechtigungsprüfung nachgewiesen: Minecrafts künstliche Befehlsquelle hat keinen Server. Das führte während des Spielerbeitritts zu einer NullPointerException. **Auf 0.1.1-dev aktualisieren**, indem nur die alte VERITY-JAR ersetzt wird. Die neue Prüfung ist durch einen Operator-Beitritt im Fabric-Testserver und durch Prüfung verweigerter Gastrechte abgedeckt. Keine Welt- oder Spielerdaten löschen.

## „Ungültige Spieldateien“ bei Installation oder Start eingrenzen

- Erscheint die Meldung beim **Importieren oder Auswählen der VERITY-JAR als Spielversion**, die bestehende Minecraft-/Fabric-Instanz verwenden und die Datei dort als Mod hinzufügen.
- Erscheint sie beim **Starten einer normalen Minecraft-Installation**, werden der genaue Launcher, die vollständige Meldung und die Version des gewählten Profils benötigt. Aus dem kurzen Meldungstext allein lässt sich weder ein defekter Mod noch eine defekte Minecraft-Installation nachweisen.
- Nennt Fabric **inkompatible Mods oder fehlende Abhängigkeiten**, die Versionsangaben in der Meldung prüfen: Minecraft 26.3, Java 25, Fabric Loader mindestens 0.20.1 und die zu 26.3 gehörende Fabric API.
- Entsteht ein Fehler **während des Spielstarts**, steht der zugehörige Fehlertext normalerweise in `logs/latest.log` des gewählten Spielverzeichnisses.

Der hochgeladene Build wurde als ZIP/JAR geprüft. Version: **0.1.1-dev**. Größe: **27.804.927 Bytes**. SHA-256: `ce2e4b057d283e4d1e83cfa666b36d3538c4dc80eded97947ee0b7d7e2555b7a`. Dies bestätigt die Identität der Datei, ersetzt jedoch keinen Client-Test auf dem Zielsystem.
