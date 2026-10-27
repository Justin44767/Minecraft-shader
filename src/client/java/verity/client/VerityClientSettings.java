package verity.client;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import verity.VerityMod;

final class VerityClientSettings {
    boolean hud = true, left, horizontal, voice = true, percentage = true, trust = true;
    int scale = 1, opacity = 85;
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("verity-client.properties");
    void load() {
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            var p = new Properties(); p.load(reader);
            hud = Boolean.parseBoolean(p.getProperty("hud", "true")); left = Boolean.parseBoolean(p.getProperty("left", "false"));
            horizontal = Boolean.parseBoolean(p.getProperty("horizontal", "false")); voice = Boolean.parseBoolean(p.getProperty("voice", "true"));
            percentage = Boolean.parseBoolean(p.getProperty("percentage", "true")); trust = Boolean.parseBoolean(p.getProperty("trust", "true"));
            scale = Math.clamp(Integer.parseInt(p.getProperty("scale", "1")), 1, 2);
            opacity = Math.clamp(Integer.parseInt(p.getProperty("opacity", "85")), 25, 100);
        } catch (IOException | IllegalArgumentException e) { VerityMod.LOGGER.warn("Could not load local VERITY display settings", e); }
    }
    void save() {
        var p = new Properties();
        p.setProperty("hud", "" + hud); p.setProperty("left", "" + left); p.setProperty("horizontal", "" + horizontal);
        p.setProperty("voice", "" + voice); p.setProperty("percentage", "" + percentage); p.setProperty("trust", "" + trust);
        p.setProperty("scale", "" + scale); p.setProperty("opacity", "" + opacity);
        try { Files.createDirectories(file.getParent()); try (Writer w = Files.newBufferedWriter(file)) { p.store(w, "VERITY local preferences; no microphone capture"); } }
        catch (IOException e) { VerityMod.LOGGER.error("Could not save local VERITY display settings", e); }
    }
}
