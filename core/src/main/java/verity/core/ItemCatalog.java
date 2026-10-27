package verity.core;

import java.util.*;
import java.util.regex.Pattern;

/** Registry-backed resolution: unknown and ambiguous requests are never guessed. */
public final class ItemCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final Set<String> registered;
    private final Map<String, String> aliases;

    public ItemCatalog(Set<String> registered, Map<String, String> aliases) {
        this.registered = Set.copyOf(registered);
        aliases.forEach((alias, id) -> {
            if (!ID.matcher(id).matches() || !registered.contains(id)) {
                throw new IllegalArgumentException("Alias references unregistered item: " + id);
            }
        });
        this.aliases = Map.copyOf(aliases);
    }

    public Optional<String> resolve(String text) {
        Set<String> found = new HashSet<>();
        var matcher = ID.matcher(text);
        while (matcher.find()) {
            String id = matcher.group();
            if (!registered.contains(id)) return Optional.empty();
            found.add(id);
        }
        aliases.forEach((alias, id) -> {
            if (Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(alias)
                    + "(?![\\p{L}\\p{N}_])").matcher(text).find()) found.add(id);
        });
        return found.size() == 1 ? Optional.of(found.iterator().next()) : Optional.empty();
    }

    public static Map<String, String> germanEnglishAliases() {
        return Map.ofEntries(
                Map.entry("diamanten", "minecraft:diamond"), Map.entry("diamant", "minecraft:diamond"),
                Map.entry("diamonds", "minecraft:diamond"), Map.entry("diamond", "minecraft:diamond"),
                Map.entry("netherite", "minecraft:netherite_ingot"),
                Map.entry("schwert", "minecraft:iron_sword"), Map.entry("sword", "minecraft:iron_sword"),
                Map.entry("spitzhacke", "minecraft:iron_pickaxe"), Map.entry("pickaxe", "minecraft:iron_pickaxe"),
                Map.entry("totem", "minecraft:totem_of_undying"),
                Map.entry("essen", "minecraft:cooked_beef"), Map.entry("food", "minecraft:cooked_beef"),
                Map.entry("redstone", "minecraft:redstone"), Map.entry("stein", "minecraft:stone"),
                Map.entry("stone", "minecraft:stone"));
    }
}
