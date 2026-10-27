package verity.core;

import java.util.*;

/** Mutable only inside the synchronized engine. Immutable snapshots are used for storage. */
public final class VerityWorldState {
    public enum Phase { FRIEND, OBSERVER, CORRUPTED, DEMON }
    public enum Visibility { PRIVATE, GROUP }
    public enum GrantKind { STARTER_BOOK, STARTER_FLASHLIGHT, RESPAWN_FLASHLIGHT, REQUESTED_ITEM }
    public record Memory(UUID requestId, long timestamp, Visibility visibility, String input, String reply) {
        public Memory {
            Objects.requireNonNull(requestId); Objects.requireNonNull(visibility);
            if (timestamp < 0 || input == null || input.length() > 512 || reply == null || reply.length() > 4096)
                throw new IllegalArgumentException("Invalid conversation memory");
        }
    }
    public record Grant(UUID id, UUID player, GrantKind kind, String itemId, int amount, long createdAt) {
        public Grant {
            Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(kind);
            if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || amount < 1 || amount > 2304 || createdAt < 0) throw new IllegalArgumentException("Invalid grant");
        }
    }
    public record ProfileSnapshot(UUID uuid, String name, int trust, boolean starterIssued,
                                  long deaths, long respawns, long lastItemAt, List<Memory> memories,
                                  List<UUID> processedRequests) {
        public ProfileSnapshot {
            Objects.requireNonNull(uuid);
            if (name == null || !name.matches("[A-Za-z0-9_]{1,16}") || trust < 0 || trust > 100
                    || deaths < 0 || respawns < 0 || respawns > deaths || lastItemAt < -1
                    || memories.size() > 200 || processedRequests.size() > 256) {
                throw new IllegalArgumentException("Invalid persisted profile");
            }
            memories = List.copyOf(memories); processedRequests = List.copyOf(processedRequests);
        }
    }
    public record Snapshot(UUID worldId, int mood, Phase phase, UUID entityId, UUID followTarget,
                           long storyProgress, Map<UUID, ProfileSnapshot> profiles, List<Grant> grants) {
        public Snapshot {
            Objects.requireNonNull(worldId); Objects.requireNonNull(phase);
            if (mood < 0 || mood > 100 || storyProgress < 0 || profiles.size() > 10000 || grants.size() > 10000)
                throw new IllegalArgumentException("Invalid persisted world");
            profiles.forEach((id, profile) -> {
                if (!id.equals(profile.uuid())) throw new IllegalArgumentException("Profile identity mismatch");
            });
            Set<UUID> grantIds = new HashSet<>();
            for (Grant g : grants) {
                if (!profiles.containsKey(g.player()) || !grantIds.add(g.id()))
                    throw new IllegalArgumentException("Invalid grant ownership or duplicate grant");
            }
            profiles = Map.copyOf(profiles); grants = List.copyOf(grants);
        }
    }

    static final class Profile {
        final UUID uuid;
        String name;
        int trust = 50;
        boolean starterIssued;
        long deaths, respawns;
        long lastItemAt = -1;
        final ArrayDeque<Memory> memories = new ArrayDeque<>();
        final LinkedHashSet<UUID> processedRequests = new LinkedHashSet<>();
        Profile(PlayerIdentity player) { uuid = player.uuid(); name = player.name(); }
        Profile(ProfileSnapshot s) {
            uuid = s.uuid(); name = s.name(); trust = s.trust(); starterIssued = s.starterIssued();
            deaths = s.deaths(); respawns = s.respawns(); lastItemAt = s.lastItemAt();
            memories.addAll(s.memories()); processedRequests.addAll(s.processedRequests());
        }
        ProfileSnapshot snapshot() {
            return new ProfileSnapshot(uuid, name, trust, starterIssued, deaths, respawns,
                    lastItemAt, List.copyOf(memories), List.copyOf(processedRequests));
        }
    }

    final UUID worldId;
    int mood = 70;
    Phase phase = Phase.FRIEND;
    UUID entityId, followTarget;
    long storyProgress;
    final Map<UUID, Profile> profiles = new HashMap<>();
    final LinkedHashMap<UUID, Grant> grants = new LinkedHashMap<>();
    public VerityWorldState(UUID worldId) { this.worldId = Objects.requireNonNull(worldId); }
    public VerityWorldState(Snapshot s) {
        worldId = s.worldId(); mood = s.mood(); phase = s.phase(); entityId = s.entityId();
        followTarget = s.followTarget(); storyProgress = s.storyProgress();
        s.profiles().forEach((id, p) -> profiles.put(id, new Profile(p)));
        s.grants().forEach(g -> grants.put(g.id(), g));
    }
    Snapshot snapshot() {
        Map<UUID, ProfileSnapshot> result = new HashMap<>();
        profiles.forEach((id, p) -> result.put(id, p.snapshot()));
        return new Snapshot(worldId, mood, phase, entityId, followTarget, storyProgress, result,
                List.copyOf(grants.values()));
    }
}
