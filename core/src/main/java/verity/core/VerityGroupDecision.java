package verity.core;

import java.util.*;

/** One server-created vote with an immutable electorate. Late joins cannot alter a live vote. */
public final class VerityGroupDecision {
    public enum Outcome { OPEN, YES, NO, TIED }
    private final UUID id = UUID.randomUUID();
    private final Set<UUID> eligible;
    private final UUID host;
    private final long deadline;
    private final Map<UUID, Boolean> votes = new HashMap<>();
    private Outcome completed;

    public VerityGroupDecision(Set<UUID> eligible, UUID host, long deadline) {
        this.eligible = Set.copyOf(eligible); this.host = Objects.requireNonNull(host);
        if (eligible.isEmpty() || !eligible.contains(host) || deadline < 0) throw new IllegalArgumentException("Invalid vote");
        this.deadline = deadline;
    }
    public UUID id() { return id; }
    public synchronized boolean vote(PlayerIdentity authenticatedActor, boolean choice, long now) {
        if (now < 0 || now >= deadline || completed != null || !eligible.contains(authenticatedActor.uuid())) return false;
        votes.put(authenticatedActor.uuid(), choice);
        return true;
    }
    public synchronized Outcome outcome(long now) {
        if (completed != null) return completed;
        if (now < deadline && votes.size() < eligible.size()) return Outcome.OPEN;
        long yes = votes.values().stream().filter(Boolean::booleanValue).count();
        long no = votes.size() - yes;
        completed = yes > no ? Outcome.YES : no > yes ? Outcome.NO
                : votes.containsKey(host) ? votes.get(host) ? Outcome.YES : Outcome.NO : Outcome.TIED;
        return completed;
    }
    public synchronized Map<UUID, Boolean> votes() { return Map.copyOf(votes); }
}
