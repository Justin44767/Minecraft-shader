package verity.core;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/** Trusted server configuration. Never populate permissions from chat or client payloads. */
public record VerityConfig(ItemPolicy items, int historyLimit, boolean storeConversations, boolean followHostOnly,
                           boolean deathKick, Duration kickDelay, int queueCapacity,
                           int perPlayerCapacity, Duration requestTimeout) {
    public VerityConfig {
        if (items == null || kickDelay == null || requestTimeout == null
                || historyLimit < 0 || historyLimit > 200 || queueCapacity < 1 || queueCapacity > 1024
                || perPlayerCapacity < 1 || perPlayerCapacity > queueCapacity
                || kickDelay.isNegative() || kickDelay.compareTo(Duration.ofMinutes(1)) > 0
                || requestTimeout.isNegative() || requestTimeout.isZero()
                || requestTimeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Invalid VERITY server configuration");
        }
    }

    public static VerityConfig defaults() {
        return new VerityConfig(new ItemPolicy(false, true, Set.of(), true, 64,
                Duration.ofSeconds(30), true, true), 40, true, true, false,
                Duration.ofSeconds(5), 64, 4, Duration.ofSeconds(15));
    }

    public record ItemPolicy(boolean enabled, boolean hostOnly, Set<UUID> whitelist,
                             boolean politenessRequired, int maxAmount, Duration cooldown,
                             boolean survivalAllowed, boolean creativeAllowed) {
        public ItemPolicy {
            whitelist = Set.copyOf(whitelist);
            if (maxAmount < 1 || maxAmount > 2304 || cooldown == null || cooldown.isNegative()
                    || cooldown.compareTo(Duration.ofDays(1)) > 0) {
                throw new IllegalArgumentException("Invalid item policy");
            }
        }
    }
}
