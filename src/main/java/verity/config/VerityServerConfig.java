package verity.config;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import verity.core.VerityConfig;

public record VerityServerConfig(VerityConfig core, UUID dedicatedHost) {
    public static VerityServerConfig load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) { try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); } }
        else {
            p.setProperty("item_giving", "false"); p.setProperty("host_only_items", "true");
            p.setProperty("item_whitelist", ""); p.setProperty("politeness_required", "true");
            p.setProperty("max_item_amount", "64"); p.setProperty("item_cooldown_seconds", "30");
            p.setProperty("survival_items", "true"); p.setProperty("creative_items", "true");
            p.setProperty("follow_host_only", "true"); p.setProperty("store_conversations", "true");
            p.setProperty("history_limit", "40"); p.setProperty("death_kick", "false");
            p.setProperty("kick_delay_seconds", "5"); p.setProperty("host_uuid", "");
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW)) {
                p.store(writer, "VERITY server authority; restart the world/server after changes. UUIDs, never player-name claims.");
            }
        }
        try {
            Set<UUID> whitelist = new HashSet<>();
            for (String id : p.getProperty("item_whitelist", "").split(",")) if (!id.isBlank()) whitelist.add(UUID.fromString(id.strip()));
            var policy = new VerityConfig.ItemPolicy(bool(p, "item_giving", false), bool(p, "host_only_items", true), whitelist,
                    bool(p, "politeness_required", true), integer(p, "max_item_amount", 64),
                    Duration.ofSeconds(integer(p, "item_cooldown_seconds", 30)), bool(p, "survival_items", true), bool(p, "creative_items", true));
            var core = new VerityConfig(policy, integer(p, "history_limit", 40), bool(p, "store_conversations", true),
                    bool(p, "follow_host_only", true), bool(p, "death_kick", false), Duration.ofSeconds(integer(p, "kick_delay_seconds", 5)),
                    64, 4, Duration.ofSeconds(15));
            String host = p.getProperty("host_uuid", "").strip();
            return new VerityServerConfig(core, host.isEmpty() ? null : UUID.fromString(host));
        } catch (IllegalArgumentException e) { throw new IOException("Invalid config in " + file, e); }
    }
    private static int integer(Properties p, String name, int defaultValue) {
        return Integer.parseInt(p.getProperty(name, Integer.toString(defaultValue)).strip());
    }
    private static boolean bool(Properties p, String name, boolean defaultValue) {
        String value = p.getProperty(name, Boolean.toString(defaultValue)).strip();
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true/false for " + name);
        return Boolean.parseBoolean(value);
    }
}
