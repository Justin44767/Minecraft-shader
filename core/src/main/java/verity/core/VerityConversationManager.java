package verity.core;

import java.util.*;
import java.util.function.Function;
import static verity.core.VerityWorldState.Visibility;

/** Thread-safe ingress for text/STT; drain is called on the authoritative server tick. */
public final class VerityConversationManager {
    public enum Submission { ACCEPTED, NOT_ADDRESSED, QUEUE_FULL, DISCONNECTED, INVALID }
    public record Session(UUID player, UUID nonce) { }
    public record Delivery(Session session, VerityEngine.Response response) { }
    private record Queued(Session session, VerityEngine.Request request) { }
    private final VerityEngine engine;
    private final VerityDialogEngine dialogue = new VerityDialogEngine();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ArrayDeque<Queued> queue = new ArrayDeque<>();
    private final Map<UUID, Integer> pending = new HashMap<>();

    public VerityConversationManager(VerityEngine engine) { this.engine = Objects.requireNonNull(engine); }

    public synchronized Session connect(UUID authenticatedPlayer) {
        disconnect(authenticatedPlayer);
        Session session = new Session(authenticatedPlayer, UUID.randomUUID());
        sessions.put(authenticatedPlayer, session);
        return session;
    }
    public synchronized void disconnect(UUID player) {
        sessions.remove(player);
        queue.removeIf(q -> q.session().player().equals(player));
        pending.remove(player);
    }
    public synchronized Submission submit(Session session, UUID requestId, String text, Visibility visibility,
                                           long receivedAt, boolean explicitlyAddressed) {
        if (session == null || !session.equals(sessions.get(session.player()))) return Submission.DISCONNECTED;
        if (requestId == null || visibility == null || text == null || text.isBlank() || text.length() > 512 || receivedAt < 0)
            return Submission.INVALID;
        if (!explicitlyAddressed && !dialogue.addressed(text)) return Submission.NOT_ADDRESSED;
        if (queue.size() >= engine.config().queueCapacity()
                || pending.getOrDefault(session.player(), 0) >= engine.config().perPlayerCapacity()) return Submission.QUEUE_FULL;
        queue.addLast(new Queued(session, new VerityEngine.Request(requestId, session.player(), text, visibility, receivedAt)));
        pending.merge(session.player(), 1, Integer::sum);
        return Submission.ACCEPTED;
    }

    /** Identity lookup must read current server permissions. Permissions are not cached on submission. */
    public synchronized List<Delivery> drain(long now, int budget, Function<UUID, PlayerIdentity> currentIdentity) {
        if (budget < 1 || budget > 64) throw new IllegalArgumentException("Tick budget must be 1–64");
        List<Delivery> deliveries = new ArrayList<>();
        for (int i = 0; i < budget && !queue.isEmpty(); i++) {
            Queued q = queue.removeFirst();
            pending.computeIfPresent(q.session().player(), (key, count) -> count <= 1 ? null : count - 1);
            if (!q.session().equals(sessions.get(q.session().player()))) continue;
            PlayerIdentity actor = currentIdentity.apply(q.session().player());
            if (actor == null || !actor.uuid().equals(q.session().player())) continue;
            deliveries.add(new Delivery(q.session(), engine.answer(q.request(), actor, now)));
        }
        return List.copyOf(deliveries);
    }
    public synchronized int pendingCount() { return queue.size(); }
}
