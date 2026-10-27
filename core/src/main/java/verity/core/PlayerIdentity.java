package verity.core;

import java.util.Objects;
import java.util.UUID;

/** Values must come from the authenticated server player, not text, STT, or a client claim. */
public record PlayerIdentity(UUID uuid, String name, boolean host, boolean operator, Mode mode) {
    public enum Mode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }

    public PlayerIdentity {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(mode);
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("Invalid Minecraft player name");
        }
    }

    public boolean administrator() { return host || operator; }
}
