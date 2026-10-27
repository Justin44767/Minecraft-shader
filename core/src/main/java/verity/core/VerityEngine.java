package verity.core;

import java.util.*;
import java.util.regex.Pattern;
import static verity.core.VerityWorldState.*;

/** Server-authoritative state machine. No Minecraft, native audio, networking, or tick-blocking I/O. */
public final class VerityEngine {
    public enum Status { ANSWERED, DENIED, DUPLICATE, EXPIRED, INVALID }
    public record Request(UUID id, UUID player, String text, Visibility visibility, long receivedAt) {
        public Request {
            Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(visibility);
            if (text == null || text.length() > 512 || receivedAt < 0) throw new IllegalArgumentException("Invalid request");
        }
    }
    public record Response(UUID requestId, UUID recipient, Visibility visibility, Status status,
                           String text, int mood, int personalTrust, List<Grant> grants) {
        public Response { grants = List.copyOf(grants); }
    }
    public record PublicView(int mood, Mood emotion, int personalTrust, Phase phase, UUID entityId, UUID followTarget) { }
    public record WorldView(int mood, Phase phase, UUID entityId, UUID followTarget) { }
    public record DeathPlan(UUID player, UUID deathId, long disconnectAt, boolean kick) { }

    private final VerityWorldState state;
    private final VerityConfig config;
    private final ItemCatalog catalog;
    private final VerityDialogEngine dialogue = new VerityDialogEngine();
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_])[-+]?\\d+(?![\\p{L}\\p{N}_])");

    public VerityEngine(VerityWorldState state, VerityConfig config, ItemCatalog catalog) {
        this.state = Objects.requireNonNull(state); this.config = Objects.requireNonNull(config);
        this.catalog = Objects.requireNonNull(catalog);
        // Disabling storage also removes restored conversation data immediately.
        for (var p : state.profiles.values()) trimMemory(p);
    }

    public synchronized Snapshot snapshot() { return state.snapshot(); }
    public synchronized WorldView worldView() { return new WorldView(state.mood, state.phase, state.entityId, state.followTarget); }
    public synchronized Encounter encounter() { return state.encounter; }
    public synchronized UUID worldId() { return state.worldId; }
    public VerityConfig config() { return config; }
    private Profile profile(PlayerIdentity actor) {
        Profile p = state.profiles.computeIfAbsent(actor.uuid(), ignored -> {
            if (state.profiles.size() >= 10000) throw new IllegalStateException("Profile limit reached");
            return new Profile(actor);
        });
        p.name = actor.name();
        return p;
    }

    public synchronized PublicView view(PlayerIdentity actor) {
        return new PublicView(state.mood, Mood.of(state.mood), profile(actor).trust, state.phase,
                state.entityId, state.followTarget);
    }

    public synchronized List<Grant> join(PlayerIdentity actor, long now) {
        if (now < 0) throw new IllegalArgumentException("Negative timestamp");
        Profile p = profile(actor);
        if (!p.starterIssued) {
            if (state.grants.size() > 9998) throw new IllegalStateException("Pending grant limit reached");
            grant(p.uuid, GrantKind.STARTER_BOOK, "minecraft:written_book", 1, now);
            grant(p.uuid, GrantKind.STARTER_FLASHLIGHT, "verity:flashlight", 1, now);
            p.starterIssued = true;
        }
        if (!state.encounter.boxIssued() && state.encounter.stage() == EncounterStage.SEALED && state.entityId == null) {
            if (state.grants.size() >= 10000) throw new IllegalStateException("Pending grant limit reached");
            grant(p.uuid, GrantKind.AWAKENING_BOX, "verity:sealed_box", 1, now);
            state.encounter = new Encounter(true, EncounterStage.SEALED, null, null, 0);
        }
        // A reconnect is never a respawn. Death rewards are created only by respawn().
        return pendingGrants(actor.uuid());
    }

    public synchronized List<Grant> pendingGrants(UUID player) {
        return state.grants.values().stream().filter(g -> g.player().equals(player)).toList();
    }

    /** Acknowledge only after successful delivery. Full inventories leave the ticket pending. */
    public synchronized boolean acknowledgeGrant(UUID player, UUID ticket) {
        Grant g = state.grants.get(ticket);
        if (g == null || !g.player().equals(player)) return false;
        state.grants.remove(ticket);
        return true;
    }
    public synchronized Grant reissueEquipment(PlayerIdentity actor, GrantKind kind, long now) {
        requireAdmin(actor);
        if (now < 0 || (kind != GrantKind.STARTER_BOOK && kind != GrantKind.STARTER_FLASHLIGHT && kind != GrantKind.AWAKENING_BOX))
            throw new IllegalArgumentException("Only book/flashlight may be reissued");
        if (kind == GrantKind.AWAKENING_BOX && state.encounter.stage() != EncounterStage.SEALED)
            throw new IllegalStateException("VERITY is already opening or awake");
        profile(actor);
        for (var g : state.grants.values()) if (g.player().equals(actor.uuid()) && g.kind() == kind) return g;
        if (state.grants.size() >= 10000) throw new IllegalStateException("Pending grant limit reached");
        if (kind == GrantKind.AWAKENING_BOX) state.encounter = new Encounter(true, EncounterStage.SEALED, null, null, 0);
        return grant(actor.uuid(), kind, kind == GrantKind.STARTER_BOOK ? "minecraft:written_book"
                : kind == GrantKind.AWAKENING_BOX ? "verity:sealed_box" : "verity:flashlight", 1, now);
    }

    private Grant grant(UUID player, GrantKind kind, String itemId, int count, long now) {
        Grant g = new Grant(UUID.randomUUID(), player, kind, itemId, count, now);
        state.grants.put(g.id(), g);
        return g;
    }

    public synchronized DeathPlan died(PlayerIdentity actor, UUID deathId, long now) {
        Objects.requireNonNull(deathId);
        if (now < 0 || now > Long.MAX_VALUE - config.kickDelay().toMillis())
            throw new IllegalArgumentException("Invalid death timestamp");
        Profile p = profile(actor);
        if (p.processedRequests.contains(deathId) || p.deaths > p.respawns) {
            return new DeathPlan(actor.uuid(), deathId, now, false);
        }
        rememberRequest(p, deathId);
        p.deaths++;
        return new DeathPlan(actor.uuid(), deathId, now + config.kickDelay().toMillis(), config.deathKick());
    }

    /** Call only for an actual post-death respawn, never for dimension changes or a totem save. */
    public synchronized List<Grant> respawn(PlayerIdentity actor, long now) {
        if (now < 0) throw new IllegalArgumentException("Negative timestamp");
        Profile p = profile(actor);
        if (p.deaths > p.respawns) {
            if (state.grants.size() >= 10000) throw new IllegalStateException("Pending grant limit reached");
            grant(actor.uuid(), GrantKind.RESPAWN_FLASHLIGHT, "verity:flashlight", 1, now);
            p.respawns = p.deaths;
        }
        return pendingGrants(actor.uuid());
    }

    public synchronized Response answer(Request request, PlayerIdentity actor, long now) {
        Profile p = profile(actor);
        if (!request.player().equals(actor.uuid())) return response(request, actor, Status.DENIED,
                "Die Sprecheridentität stimmt nicht überein.", List.of());
        if (now < request.receivedAt() || now - request.receivedAt() >= config.requestTimeout().toMillis())
            return response(request, actor, Status.EXPIRED, "Die Anfrage ist abgelaufen. Bitte wiederhole sie.", List.of());
        if (p.processedRequests.contains(request.id()))
            return response(request, actor, Status.DUPLICATE, "", List.of());
        VerityDialogEngine.Analysis a;
        try { a = dialogue.analyze(request.text()); }
        catch (IllegalArgumentException e) {
            return response(request, actor, Status.INVALID, "Bitte sende 1–512 lesbare Zeichen.", List.of());
        }
        rememberRequest(p, request.id());
        state.mood = Mood.clamp(state.mood + a.moodDelta());
        p.trust = Mood.clamp(p.trust + a.trustDelta());
        String lastTopic = p.memories.stream().filter(m -> m.visibility() == Visibility.PRIVATE)
                .reduce((first, last) -> last).map(Memory::input).orElse(null);
        String text = dialogue.reply(a, actor, state.mood, lastTopic);
        Status status = Status.ANSWERED;
        List<Grant> grants = List.of();
        switch (a.intent()) {
            case ITEMS -> {
                String denial = itemDenial(a, actor, p, now);
                Optional<String> item = catalog.resolve(a.normalized());
                int count = parseCount(a.normalized());
                if (denial != null) { text = denial; status = Status.DENIED; }
                else if (item.isEmpty() || count < 1 || count > config.items().maxAmount()) {
                    text = "Bitte nenne genau ein registriertes Item und eine Menge zwischen 1 und "
                            + config.items().maxAmount() + ", zum Beispiel: gib mir bitte 8 minecraft:diamond.";
                } else {
                    Grant g = grant(actor.uuid(), GrantKind.REQUESTED_ITEM, item.get(), count, now);
                    grants = List.of(g);
                    p.lastItemAt = now;
                    text = "Für dich, " + actor.name() + ": " + count + " × " + item.get()
                            + ". Die Übergabe ist vorgemerkt; ein volles Inventar wird später erneut geprüft.";
                }
            }
            case FOLLOW, STAY, LOOK -> {
                if (config.followHostOnly() && !actor.administrator()) {
                    text = "Nur der Host oder ein Operator darf mein Follow-Verhalten ändern.";
                    status = Status.DENIED;
                } else {
                    if (a.intent() != VerityDialogEngine.Intent.LOOK)
                        state.followTarget = a.intent() == VerityDialogEngine.Intent.STAY ? null : actor.uuid();
                    text = a.intent() == VerityDialogEngine.Intent.STAY ? "Ich bleibe hier."
                            : a.intent() == VerityDialogEngine.Intent.LOOK ? "Ich sehe dich, " + actor.name() + "."
                            : "Ich folge dir, " + actor.name() + ".";
                }
            }
            case STORY_YES, STORY_NO -> {
                boolean trust = a.intent() == VerityDialogEngine.Intent.STORY_YES;
                state.mood = Mood.clamp(state.mood + (trust ? 5 : -5));
                p.trust = Mood.clamp(p.trust + (trust ? 5 : -5));
                text = trust ? "Danke, " + actor.name() + ". Ich werde mich an dein Vertrauen erinnern."
                        : "Du vertraust mir also nicht, " + actor.name() + ". Ich merke mir deine Entscheidung.";
            }
            default -> { }
        }
        // Recalling private content must remain private, even if requested in group chat.
        Visibility visibility = a.intent() == VerityDialogEngine.Intent.MEMORY
                ? Visibility.PRIVATE : request.visibility();
        if (config.storeConversations() && config.historyLimit() > 0) {
            p.memories.addLast(new Memory(request.id(), now, visibility, request.text(), text));
            trimMemory(p);
        }
        return new Response(request.id(), actor.uuid(), visibility, status, text, state.mood, p.trust, grants);
    }

    private String itemDenial(VerityDialogEngine.Analysis a, PlayerIdentity actor, Profile p, long now) {
        var policy = config.items();
        if (!policy.enabled()) return "Item Giving ist auf diesem Server deaktiviert.";
        if (policy.hostOnly() && !actor.host()) return "Item Giving ist nur für den Host freigegeben.";
        if (!policy.whitelist().isEmpty() && !policy.whitelist().contains(actor.uuid()) && !actor.host())
            return "Du stehst nicht auf der Item-Whitelist.";
        if (actor.mode() == PlayerIdentity.Mode.SPECTATOR || actor.mode() == PlayerIdentity.Mode.ADVENTURE
                || actor.mode() == PlayerIdentity.Mode.SURVIVAL && !policy.survivalAllowed()
                || actor.mode() == PlayerIdentity.Mode.CREATIVE && !policy.creativeAllowed())
            return "Item Giving ist in deinem Spielmodus gesperrt.";
        if (policy.politenessRequired() && !a.polite()) return "Frag mich bitte höflich.";
        if (!VerityDialogEngine.words(a.normalized(), "mir|me|ich brauche|i need|i want|für mich|for me"))
            return "Für wen sind die Items? Ich kann nur dir selbst etwas geben. Bitte sage zum Beispiel: gib mir bitte 8 Diamanten.";
        if (p.lastItemAt >= 0 && (now < p.lastItemAt || now - p.lastItemAt < policy.cooldown().toMillis()))
            return "Bitte warte, bevor du erneut nach Items fragst.";
        if (state.grants.size() >= 10000 || state.grants.values().stream().anyMatch(
                g -> g.player().equals(actor.uuid()) && g.kind() == GrantKind.REQUESTED_ITEM))
            return "Es wartet noch eine Item-Übergabe auf dich. Schaffe zuerst Platz im Inventar.";
        return null;
    }

    private static int parseCount(String text) {
        // Digits inside registry identifiers (e.g. mod:stone_2) are not quantities.
        String withoutIds = text.replaceAll("[a-z0-9_.-]+:[a-z0-9_./-]+", "");
        var matcher = NUMBER.matcher(withoutIds);
        if (!matcher.find()) return withoutIds.codePoints().anyMatch(Character::isDigit) ? -1 : 1;
        String first = matcher.group();
        if (matcher.find()) return -1;
        try { return Integer.parseInt(first); }
        catch (NumberFormatException e) { return -1; }
    }

    private Response response(Request r, PlayerIdentity actor, Status status, String text, List<Grant> grants) {
        return new Response(r.id(), actor.uuid(), Visibility.PRIVATE, status, text, state.mood,
                profile(actor).trust, grants);
    }
    private void trimMemory(Profile p) {
        int limit = config.storeConversations() ? config.historyLimit() : 0;
        while (p.memories.size() > limit) p.memories.removeFirst();
    }
    private void rememberRequest(Profile p, UUID id) {
        p.processedRequests.add(id);
        while (p.processedRequests.size() > 256) p.processedRequests.removeFirst();
    }
    public synchronized void forget(PlayerIdentity actor) { profile(actor).memories.clear(); }
    public synchronized void attackedBy(PlayerIdentity authenticatedAttacker) {
        state.mood = Mood.clamp(state.mood - 10);
        var p = profile(authenticatedAttacker); p.trust = Mood.clamp(p.trust - 15);
    }
    public synchronized void setMood(PlayerIdentity actor, int value) {
        requireAdmin(actor);
        if (value < 0 || value > 100) throw new IllegalArgumentException("Mood must be 0–100");
        state.mood = value;
    }
    public synchronized UUID claimEntity(PlayerIdentity actor, UUID proposed) {
        requireAdmin(actor); Objects.requireNonNull(proposed);
        UUID result = claimLoadedEntity(proposed);
        state.encounter = new Encounter(true, EncounterStage.AWAKE, null, actor.uuid(), 60);
        return result;
    }
    /** Trusted server entity-load reconciliation. This is never exposed as a client packet handler. */
    public synchronized UUID claimLoadedEntity(UUID proposed) {
        Objects.requireNonNull(proposed);
        if (state.entityId == null) {
            state.entityId = proposed;
            if (state.encounter.stage() == EncounterStage.SEALED)
                state.encounter = new Encounter(true, EncounterStage.AWAKE, null, null, 60);
        }
        return state.entityId;
    }
    public synchronized void releaseEntity(PlayerIdentity actor, UUID expected) {
        requireAdmin(actor);
        if (!Objects.equals(expected, state.entityId)) throw new IllegalStateException("Entity identity changed");
        state.entityId = null; state.followTarget = null;
        state.encounter = Encounter.sealed();
    }
    /** Called only after vanilla validates an authenticated player's block interaction. */
    public synchronized boolean beginUnboxing(PlayerIdentity actor, WorldPoint location) {
        Objects.requireNonNull(location);
        if (actor.mode() == PlayerIdentity.Mode.SPECTATOR || state.encounter.stage() != EncounterStage.SEALED) return false;
        profile(actor);
        state.encounter = new Encounter(true, EncounterStage.OPENING, location, actor.uuid(), 0);
        if (!config.followHostOnly() || actor.administrator()) state.followTarget = actor.uuid();
        return true;
    }
    public synchronized int tickUnboxing() {
        var e = state.encounter;
        if (e.stage() != EncounterStage.OPENING) return e.elapsed();
        int elapsed = Math.min(60, e.elapsed() + 1);
        state.encounter = new Encounter(true, e.stage(), e.box(), e.opener(), elapsed);
        return elapsed;
    }
    public synchronized UUID reserveAwakening() {
        if (state.encounter.stage() != EncounterStage.OPENING || state.encounter.elapsed() < 60)
            throw new IllegalStateException("The seal has not opened yet");
        if (state.entityId == null) state.entityId = UUID.randomUUID();
        return state.entityId;
    }
    public synchronized boolean finishAwakening(UUID entity) {
        if (!Objects.equals(entity, state.entityId) || state.encounter.stage() != EncounterStage.OPENING
                || state.encounter.elapsed() < 60) return false;
        var e = state.encounter;
        state.encounter = new Encounter(true, EncounterStage.AWAKE, e.box(), e.opener(), 60);
        return true;
    }
    public synchronized void packIntoBox(PlayerIdentity actor, long now) {
        requireAdmin(actor);
        if (state.encounter.stage() != EncounterStage.AWAKE || state.entityId == null)
            throw new IllegalStateException("VERITY is not awake");
        state.followTarget = null;
        state.encounter = new Encounter(true, EncounterStage.SEALED, null, null, 0);
        reissueEquipment(actor, GrantKind.AWAKENING_BOX, now);
    }
    public synchronized void cancelUnboxing(long now) {
        var e = state.encounter;
        if (e.stage() != EncounterStage.OPENING) return;
        state.encounter = new Encounter(true, EncounterStage.SEALED, null, null, 0);
        if (state.grants.size() < 10000 && state.grants.values().stream().noneMatch(g -> g.kind() == GrantKind.AWAKENING_BOX))
            grant(e.opener(), GrantKind.AWAKENING_BOX, "verity:sealed_box", 1, now);
    }
    public synchronized void setPhase(PlayerIdentity actor, Phase phase) {
        requireAdmin(actor); state.phase = Objects.requireNonNull(phase);
    }
    /** Server-owned active play time. Personality depends on both elapsed story and real mood. */
    public synchronized Phase advanceStory(int ticks, long chapterTicks) {
        if (ticks < 0 || ticks > 1200 || chapterTicks < 20 || chapterTicks > 20L * 60 * 60 * 24)
            throw new IllegalArgumentException("Invalid story clock");
        if (state.encounter.stage() != EncounterStage.AWAKE) return state.phase;
        state.storyProgress = Math.min(Long.MAX_VALUE - ticks, state.storyProgress) + ticks;
        if (state.phase == Phase.DEMON) {
            if (state.mood >= 80) state.phase = Phase.OBSERVER;
        } else if (state.phase == Phase.CORRUPTED && state.mood >= 75) state.phase = Phase.OBSERVER;
        else if (state.storyProgress >= chapterTicks * 3 && state.mood < 20) state.phase = Phase.DEMON;
        else if (state.storyProgress >= chapterTicks * 2 && state.mood < 45) state.phase = Phase.CORRUPTED;
        else if (state.storyProgress >= chapterTicks && state.phase == Phase.FRIEND) state.phase = Phase.OBSERVER;
        return state.phase;
    }
    public synchronized long storyTime() { return state.storyProgress; }
    public synchronized void disturbMood(int delta) { state.mood = Mood.clamp(state.mood + Math.clamp(delta, -10, 10)); }
    public synchronized void lightRescue(PlayerIdentity actor) {
        state.mood = Mood.clamp(state.mood + 2);
        var p = profile(actor); p.trust = Mood.clamp(p.trust + 1);
    }
    private static void requireAdmin(PlayerIdentity actor) {
        if (!actor.administrator()) throw new SecurityException("Host/operator permission required");
    }
}
