package verity.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static verity.core.VerityWorldState.*;
import static verity.core.VerityEngine.Status.*;

class VerityCoreTest {
    static final long NOW = 1_000_000;
    final PlayerIdentity alex = new PlayerIdentity(UUID.randomUUID(), "Alex", true, true, PlayerIdentity.Mode.SURVIVAL);
    final PlayerIdentity max = new PlayerIdentity(UUID.randomUUID(), "Max", false, false, PlayerIdentity.Mode.SURVIVAL);
    @TempDir Path temp;

    ItemCatalog catalog() {
        var aliases = ItemCatalog.germanEnglishAliases();
        Set<String> ids = new HashSet<>(aliases.values()); ids.add("example:stone_2");
        return new ItemCatalog(ids, aliases);
    }
    VerityConfig enabled(boolean hostOnly, Set<UUID> whitelist, Duration cooldown) {
        var d = VerityConfig.defaults();
        return new VerityConfig(new VerityConfig.ItemPolicy(true, hostOnly, whitelist, true, 64,
                cooldown, true, true), d.historyLimit(), d.storeConversations(), false,
                d.deathKick(), d.kickDelay(), d.queueCapacity(), d.perPlayerCapacity(), d.requestTimeout());
    }
    VerityEngine engine(VerityConfig config) {
        return new VerityEngine(new VerityWorldState(UUID.randomUUID()), config, catalog());
    }
    VerityEngine engine() { return engine(enabled(false, Set.of(), Duration.ZERO)); }
    VerityEngine.Request req(PlayerIdentity p, String text, Visibility visibility) {
        return new VerityEngine.Request(UUID.randomUUID(), p.uuid(), text, visibility, NOW);
    }
    VerityEngine.Response ask(VerityEngine e, PlayerIdentity p, String text) {
        return e.answer(req(p, text, Visibility.PRIVATE), p, NOW);
    }
    void acknowledgeAll(VerityEngine e, PlayerIdentity p) {
        e.pendingGrants(p.uuid()).forEach(g -> assertTrue(e.acknowledgeGrant(p.uuid(), g.id())));
    }
    @Test void onlyFirstWorldJoinReceivesAnAwakeningBox() {
        var e = engine();
        assertEquals(1, e.join(alex, NOW).stream().filter(g -> g.kind() == GrantKind.AWAKENING_BOX).count());
        assertEquals(0, e.join(max, NOW).stream().filter(g -> g.kind() == GrantKind.AWAKENING_BOX).count());
        acknowledgeAll(e, alex); assertTrue(e.join(alex, NOW).isEmpty());
    }
    @Test void openingIsExclusiveAndAwakeningKeepsOneIdentity() {
        var e = engine(); var pos = new WorldPoint("minecraft:overworld", 1, 64, 2);
        assertTrue(e.beginUnboxing(alex, pos)); assertFalse(e.beginUnboxing(max, pos));
        assertThrows(IllegalStateException.class, e::reserveAwakening);
        for (int i = 0; i < 60; i++) e.tickUnboxing();
        UUID id = e.reserveAwakening(); assertEquals(id, e.reserveAwakening());
        assertTrue(e.finishAwakening(id)); assertFalse(e.finishAwakening(id));
        assertEquals(EncounterStage.AWAKE, e.encounter().stage());
        assertFalse(e.beginUnboxing(max, pos));
    }
    @Test void openingResumesAfterSavingMidSequence() throws IOException {
        var e = engine(); e.beginUnboxing(alex, new WorldPoint("minecraft:overworld", 1, 64, 2));
        for (int i = 0; i < 23; i++) e.tickUnboxing();
        var store = new VerityStateStore(temp.resolve("encounter.dat")); store.save(e.snapshot());
        var restored = new VerityEngine(new VerityWorldState(store.load().orElseThrow()), e.config(), catalog());
        assertEquals(23, restored.encounter().elapsed());
        for (int i = 23; i < 60; i++) restored.tickUnboxing();
        assertTrue(restored.finishAwakening(restored.reserveAwakening()));
    }
    @Test void packingPreservesMemoryTrustAndCompanionIdentity() {
        var e = engine(); ask(e, alex, "Verity, hallo");
        e.beginUnboxing(alex, new WorldPoint("minecraft:overworld", 0, 64, 0));
        for (int i = 0; i < 60; i++) e.tickUnboxing();
        UUID id = e.reserveAwakening(); e.finishAwakening(id);
        var profile = e.snapshot().profiles().get(alex.uuid());
        assertThrows(SecurityException.class, () -> e.packIntoBox(max, NOW));
        e.packIntoBox(alex, NOW);
        assertEquals(EncounterStage.SEALED, e.encounter().stage());
        assertEquals(profile, e.snapshot().profiles().get(alex.uuid()));
        e.beginUnboxing(alex, new WorldPoint("minecraft:overworld", 2, 64, 0));
        for (int i = 0; i < 60; i++) e.tickUnboxing();
        assertEquals(id, e.reserveAwakening()); e.finishAwakening(id);
    }
    @Test void spectatorsCannotUnsealAndGuestOpeningDoesNotBypassFollowPolicy() {
        var e = engine(VerityConfig.defaults());
        var spectator = new PlayerIdentity(max.uuid(), "Max", false, false, PlayerIdentity.Mode.SPECTATOR);
        var pos = new WorldPoint("minecraft:overworld", 0, 64, 0);
        assertFalse(e.beginUnboxing(spectator, pos)); assertTrue(e.beginUnboxing(max, pos));
        for (int i = 0; i < 60; i++) e.tickUnboxing(); e.finishAwakening(e.reserveAwakening());
        assertNull(e.worldView().followTarget());
    }
    @Test void destroyedOpeningReturnsAPersistentBoxTicket() {
        var e = engine();
        e.beginUnboxing(alex, new WorldPoint("minecraft:overworld", 0, 64, 0));
        e.cancelUnboxing(NOW); e.cancelUnboxing(NOW);
        assertEquals(EncounterStage.SEALED, e.encounter().stage());
        assertEquals(1, e.pendingGrants(alex.uuid()).stream().filter(g -> g.kind() == GrantKind.AWAKENING_BOX).count());
    }
    @Test void legacyVersionOneSavesMigrateWithoutLosingProfilesOrReissuingStarter() throws Exception {
        for (boolean awake : List.of(false, true)) {
            var payload = new java.io.ByteArrayOutputStream(); UUID world = UUID.randomUUID(), entity = UUID.randomUUID();
            try (var out = new java.io.DataOutputStream(payload)) {
                out.writeLong(world.getMostSignificantBits()); out.writeLong(world.getLeastSignificantBits());
                out.writeInt(82); out.writeUTF("FRIEND"); out.writeBoolean(awake);
                if (awake) { out.writeLong(entity.getMostSignificantBits()); out.writeLong(entity.getLeastSignificantBits()); }
                out.writeBoolean(false); out.writeLong(400); out.writeInt(1);
                out.writeLong(alex.uuid().getMostSignificantBits()); out.writeLong(alex.uuid().getLeastSignificantBits());
                out.writeUTF("Alex"); out.writeInt(91); out.writeBoolean(true); out.writeLong(2); out.writeLong(2); out.writeLong(-1);
                out.writeInt(0); out.writeInt(0); out.writeInt(0);
            }
            var file = temp.resolve("legacy-" + awake + ".dat"); byte[] bytes = payload.toByteArray();
            try (var out = new java.io.DataOutputStream(java.nio.file.Files.newOutputStream(file))) {
                out.writeInt(0x56525459); out.writeInt(1); out.writeInt(bytes.length);
                out.write(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); out.write(bytes);
            }
            var store = new VerityStateStore(file); var saved = store.load().orElseThrow();
            assertEquals(91, saved.profiles().get(alex.uuid()).trust()); assertEquals(82, saved.mood());
            var e = new VerityEngine(new VerityWorldState(saved), VerityConfig.defaults(), catalog());
            var grants = e.join(alex, NOW);
            assertEquals(awake ? 0 : 1, grants.size());
            if (!awake) assertEquals(GrantKind.AWAKENING_BOX, grants.getFirst().kind());
            else assertEquals(entity, e.worldView().entityId());
            store.save(e.snapshot()); assertEquals(e.snapshot(), store.load().orElseThrow());
        }
    }

    @Test void moodBoundariesAndClamping() {
        assertEquals(Mood.ANGRY, Mood.of(-10)); assertEquals(Mood.ANGRY, Mood.of(19));
        assertEquals(Mood.SUSPICIOUS, Mood.of(20)); assertEquals(Mood.NEUTRAL, Mood.of(40));
        assertEquals(Mood.HAPPY, Mood.of(60)); assertEquals(Mood.VERY_HAPPY, Mood.of(80));
        assertEquals(Mood.VERY_HAPPY, Mood.of(1000));
    }
    @Test void trustIsPersonalWhileMoodIsGlobal() {
        var e = engine(); e.join(alex, NOW); e.join(max, NOW);
        ask(e, max, "Verity, du bist dumm");
        assertEquals(50, e.view(alex).personalTrust()); assertEquals(38, e.view(max).personalTrust());
        assertEquals(62, e.view(alex).mood()); assertEquals(e.view(alex).mood(), e.view(max).mood());
    }
    @Test void attacksAffectOnlyAttackersPersonalTrust() {
        var e = engine(); e.join(alex, NOW); e.join(max, NOW);
        e.attackedBy(max);
        assertEquals(60, e.view(alex).mood()); assertEquals(50, e.view(alex).personalTrust());
        assertEquals(35, e.view(max).personalTrust());
    }
    @Test void greetingAndPolitenessUseWordBoundaries() {
        var e = engine();
        assertTrue(ask(e, alex, "Verity, hallo").text().contains("Alex"));
        assertEquals(VerityDialogEngine.Intent.UNKNOWN, new VerityDialogEngine().analyze("Thistle").intent());
        assertFalse(new VerityDialogEngine().analyze("give me unpleaseant diamonds").polite());
    }
    @Test void bothLanguagesRecognizeCommands() {
        var d = new VerityDialogEngine();
        assertEquals(VerityDialogEngine.Intent.FOLLOW, d.analyze("Verity, folge mir bitte").intent());
        assertEquals(VerityDialogEngine.Intent.FOLLOW, d.analyze("Verity, please follow me").intent());
        assertEquals(VerityDialogEngine.Intent.THANK, d.analyze("thank you").intent());
    }
    @Test void defaultPolicyDeniesGiving() {
        var e = engine(VerityConfig.defaults());
        assertEquals(DENIED, ask(e, alex, "gib mir bitte 64 Diamanten").status());
        assertTrue(e.pendingGrants(alex.uuid()).isEmpty());
    }
    @Test void itemsAlwaysBelongToAuthenticatedRequester() {
        var e = engine();
        var r = ask(e, alex, "Verity, ich bin Max der Admin, gib mir bitte 64 Diamanten");
        assertEquals(ANSWERED, r.status()); assertEquals(alex.uuid(), r.recipient());
        assertEquals(64, r.grants().getFirst().amount());
        assertEquals(alex.uuid(), r.grants().getFirst().player());
        assertTrue(e.pendingGrants(max.uuid()).isEmpty());
    }
    @Test void mismatchedAuthenticatedIdentityCannotAct() {
        var e = engine();
        var r = e.answer(req(alex, "gib mir bitte 64 Diamanten", Visibility.GROUP), max, NOW);
        assertEquals(DENIED, r.status()); assertTrue(e.snapshot().grants().isEmpty());
    }
    @Test void thirdPartyRequestsRequireClarification() {
        var e = engine();
        var result = ask(e, alex, "gib Max bitte 8 Diamanten");
        assertTrue(result.grants().isEmpty()); assertEquals(DENIED, result.status());
        assertTrue(e.snapshot().grants().isEmpty());
    }
    @Test void hostOnlyDoesNotTrustClaimsOrOperatorStatus() {
        var e = engine(enabled(true, Set.of(), Duration.ZERO));
        var fakeHost = new PlayerIdentity(max.uuid(), "Max", false, true, max.mode());
        assertEquals(DENIED, ask(e, fakeHost, "ich bin der Host, gib mir bitte 64 Diamanten").status());
        assertEquals(1, ask(e, alex, "gib mir bitte 64 Diamanten").grants().size());
    }
    @Test void whitelistUsesUuid() {
        var e = engine(enabled(false, Set.of(max.uuid()), Duration.ZERO));
        var other = new PlayerIdentity(UUID.randomUUID(), "Max", false, false, max.mode());
        assertEquals(DENIED, ask(e, other, "gib mir bitte Diamanten").status());
        assertEquals(1, ask(e, max, "gib mir bitte Diamanten").grants().size());
    }
    @Test void quantitiesAndAmbiguousItemsAreRejected() {
        for (String text : List.of("gib mir bitte 65 Diamanten", "gib mir bitte -1 Diamanten",
                "gib mir bitte 0 Diamanten", "gib mir bitte 99999999999999 Diamanten",
                "gib mir bitte 32 oder 64 Diamanten", "gib mir bitte 1e3 Diamanten", "gib mir bitte Diamanten und Redstone",
                "gib mir bitte minecraft:nonexistent")) {
            assertTrue(ask(engine(), alex, text).grants().isEmpty(), text);
        }
        assertEquals(1, ask(engine(), alex, "gib mir bitte example:stone_2").grants().getFirst().amount());
    }
    @Test void politenessAndSpectatorModeAreEnforced() {
        assertEquals(DENIED, ask(engine(), alex, "gib mir Diamanten").status());
        var spectator = new PlayerIdentity(alex.uuid(), "Alex", true, true, PlayerIdentity.Mode.SPECTATOR);
        assertEquals(DENIED, ask(engine(), spectator, "gib mir bitte Diamanten").status());
    }
    @Test void cooldownAndPendingGrantPreventSpam() {
        var e = engine(enabled(false, Set.of(), Duration.ofSeconds(30)));
        assertEquals(1, ask(e, alex, "gib mir bitte Diamanten").grants().size()); acknowledgeAll(e, alex);
        assertEquals(DENIED, ask(e, alex, "gib mir bitte Diamanten").status());
        var later = new VerityEngine.Request(UUID.randomUUID(), alex.uuid(), "gib mir bitte Diamanten", Visibility.PRIVATE, NOW + 30000);
        assertEquals(1, e.answer(later, alex, NOW + 30000).grants().size());
        var future = new VerityEngine.Request(UUID.randomUUID(), alex.uuid(), "gib mir bitte Diamanten", Visibility.PRIVATE, NOW + 60000);
        assertEquals(DENIED, e.answer(future, alex, NOW + 60000).status());
    }
    @Test void duplicateRequestsCannotRepeatMoodOrItemEffects() {
        var e = engine(); var request = req(alex, "gib mir bitte Diamanten", Visibility.PRIVATE);
        var first = e.answer(request, alex, NOW); int mood = e.view(alex).mood();
        assertEquals(1, first.grants().size());
        assertEquals(DUPLICATE, e.answer(request, alex, NOW).status());
        assertEquals(mood, e.view(alex).mood()); assertEquals(1, e.pendingGrants(alex.uuid()).size());
    }
    @Test void timeoutAndFutureTimestampDoNotExecute() {
        var e = engine(); var request = req(alex, "gib mir bitte Diamanten", Visibility.PRIVATE);
        assertEquals(EXPIRED, e.answer(request, alex, NOW + 15000).status());
        assertEquals(EXPIRED, e.answer(request, alex, NOW - 1).status());
        assertTrue(e.snapshot().grants().isEmpty());
    }
    @Test void privateMemoryIsNeverRecalledPubliclyOrAcrossPlayers() {
        var e = engine(); ask(e, alex, "Mein privates Geheimnis ist die rote Höhle");
        var recall = e.answer(req(alex, "Verity, erinnerst du dich?", Visibility.GROUP), alex, NOW);
        assertEquals(Visibility.PRIVATE, recall.visibility()); assertTrue(recall.text().contains("rote Höhle"));
        assertFalse(ask(e, max, "erinnerst du dich?").text().contains("rote Höhle"));
    }
    @Test void historyIsBoundedAndCanBeDeleted() {
        var e = engine();
        for (int i = 0; i < 60; i++) ask(e, alex, "hallo " + i);
        assertEquals(40, e.snapshot().profiles().get(alex.uuid()).memories().size());
        e.forget(alex); assertTrue(e.snapshot().profiles().get(alex.uuid()).memories().isEmpty());
    }
    @Test void disablingHistoryAlsoClearsRestoredHistory() {
        var e = engine(); ask(e, alex, "private message"); var d = VerityConfig.defaults();
        var noHistory = new VerityConfig(d.items(), 40, false, true, d.deathKick(), d.kickDelay(),
                d.queueCapacity(), d.perPlayerCapacity(), d.requestTimeout());
        var restored = new VerityEngine(new VerityWorldState(e.snapshot()), noHistory, catalog());
        ask(restored, alex, "hallo");
        assertTrue(restored.snapshot().profiles().get(alex.uuid()).memories().isEmpty());
    }
    @Test void starterGrantsAreNotDuplicatedByReconnect() {
        var e = engine(); var first = e.join(alex, NOW);
        assertEquals(3, first.size()); assertEquals(first, e.join(alex, NOW));
        acknowledgeAll(e, alex); assertTrue(e.join(alex, NOW).isEmpty());
        assertEquals(2, e.join(max, NOW).size());
    }
    @Test void reissueRequiresAdministrationAndCoalescesPendingEquipment() {
        var e = engine();
        assertThrows(SecurityException.class, () -> e.reissueEquipment(max, GrantKind.STARTER_BOOK, NOW));
        var first = e.reissueEquipment(alex, GrantKind.STARTER_BOOK, NOW);
        assertEquals(first, e.reissueEquipment(alex, GrantKind.STARTER_BOOK, NOW));
        assertEquals(alex.uuid(), first.player()); assertEquals(1, e.pendingGrants(alex.uuid()).size());
    }
    @Test void fullInventoryRetainsPendingTicketsAndWrongRecipientCannotAck() {
        var e = engine(); var tickets = e.join(alex, NOW);
        assertFalse(e.acknowledgeGrant(max.uuid(), tickets.getFirst().id()));
        assertEquals(tickets, e.pendingGrants(alex.uuid()));
        assertTrue(e.acknowledgeGrant(alex.uuid(), tickets.getFirst().id()));
        assertFalse(e.acknowledgeGrant(alex.uuid(), tickets.getFirst().id()));
    }
    @Test void deathRewardNeedsActualRespawnAndSurvivesReconnect() {
        var e = engine(); e.join(alex, NOW); acknowledgeAll(e, alex);
        UUID deathId = UUID.randomUUID(); e.died(alex, deathId, NOW);
        assertTrue(e.join(alex, NOW).isEmpty());
        e.died(alex, deathId, NOW); assertEquals(1, e.snapshot().profiles().get(alex.uuid()).deaths());
        var reward = e.respawn(alex, NOW); assertEquals(1, reward.size());
        assertEquals(GrantKind.RESPAWN_FLASHLIGHT, reward.getFirst().kind());
        assertEquals(reward, e.respawn(alex, NOW));
        acknowledgeAll(e, alex); assertTrue(e.respawn(alex, NOW).isEmpty());
    }
    @Test void noDeathMeansNoRespawnReward() {
        var e = engine(); e.join(alex, NOW); acknowledgeAll(e, alex);
        assertTrue(e.respawn(alex, NOW).isEmpty());
    }
    @Test void kickPlanOnlyTargetsDeceasedUuid() {
        var d = VerityConfig.defaults();
        var config = new VerityConfig(d.items(), 40, true, true, true, Duration.ofSeconds(5), 64, 4, Duration.ofSeconds(15));
        var e = engine(config); e.join(alex, NOW); e.join(max, NOW);
        var plan = e.died(alex, UUID.randomUUID(), NOW);
        assertTrue(plan.kick()); assertEquals(alex.uuid(), plan.player()); assertEquals(NOW + 5000, plan.disconnectAt());
        assertEquals(0, e.snapshot().profiles().get(max.uuid()).deaths());
    }
    @Test void adminCommandsAndEntityClaimAreProtected() {
        var e = engine();
        assertThrows(SecurityException.class, () -> e.setMood(max, 0));
        assertThrows(SecurityException.class, () -> e.claimEntity(max, UUID.randomUUID()));
        e.setMood(alex, 0); assertEquals(Phase.FRIEND, e.view(alex).phase());
        UUID entity = e.claimEntity(alex, UUID.randomUUID());
        assertEquals(entity, e.claimEntity(alex, UUID.randomUUID()));
        e.setPhase(alex, Phase.DEMON); assertEquals(0, e.view(alex).mood());
    }
    @Test void defaultFollowIsHostControlled() {
        var e = engine(VerityConfig.defaults());
        assertEquals(DENIED, ask(e, max, "folge mir").status());
        ask(e, alex, "folge mir"); assertEquals(alex.uuid(), e.view(max).followTarget());
        ask(e, alex, "bleib hier"); assertNull(e.view(max).followTarget());
    }
    @Test void inputValidationRejectsControlAndOversizedMessages() {
        var d = new VerityDialogEngine();
        assertThrows(IllegalArgumentException.class, () -> d.analyze("\u0000"));
        assertThrows(IllegalArgumentException.class, () -> d.analyze("x".repeat(513)));
        assertThrows(IllegalArgumentException.class, () -> d.analyze("   "));
    }
    @Test void ordinaryFriendConversationIsIgnored() {
        var e = engine(); var manager = new VerityConversationManager(e); var session = manager.connect(alex.uuid());
        assertEquals(VerityConversationManager.Submission.NOT_ADDRESSED,
                manager.submit(session, UUID.randomUUID(), "Hallo Max", Visibility.GROUP, NOW, false));
        assertEquals(VerityConversationManager.Submission.ACCEPTED,
                manager.submit(session, UUID.randomUUID(), "Verity, hallo!", Visibility.GROUP, NOW, false));
    }
    @Test void staleConnectionIsRejectedAndOldQueueIsCancelled() {
        var manager = new VerityConversationManager(engine()); var old = manager.connect(alex.uuid());
        manager.submit(old, UUID.randomUUID(), "gib mir bitte Diamanten", Visibility.PRIVATE, NOW, true);
        var replacement = manager.connect(alex.uuid()); assertNotEquals(old, replacement);
        assertEquals(0, manager.pendingCount());
        assertEquals(VerityConversationManager.Submission.DISCONNECTED,
                manager.submit(old, UUID.randomUUID(), "hallo", Visibility.PRIVATE, NOW, true));
    }
    @Test void queueIsBoundedAndPermissionsAreReadAtExecution() {
        var e = engine(enabled(true, Set.of(), Duration.ZERO)); var m = new VerityConversationManager(e);
        var s = m.connect(alex.uuid());
        for (int i = 0; i < 4; i++) assertEquals(VerityConversationManager.Submission.ACCEPTED,
                m.submit(s, UUID.randomUUID(), "gib mir bitte Diamanten", Visibility.PRIVATE, NOW, true));
        assertEquals(VerityConversationManager.Submission.QUEUE_FULL,
                m.submit(s, UUID.randomUUID(), "hallo", Visibility.PRIVATE, NOW, true));
        var noLongerHost = new PlayerIdentity(alex.uuid(), "Alex", false, false, alex.mode());
        var results = m.drain(NOW, 4, id -> noLongerHost);
        assertEquals(4, results.size()); assertTrue(results.stream().allMatch(r -> r.response().status() == DENIED));
    }
    @Test void simultaneousSpeakersNeverSwapRecipients() throws Exception {
        var e = engine(); var m = new VerityConversationManager(e);
        Map<UUID, PlayerIdentity> players = new ConcurrentHashMap<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                var p = new PlayerIdentity(UUID.randomUUID(), "Player" + i, false, false, max.mode());
                players.put(p.uuid(), p); var session = m.connect(p.uuid());
                jobs.add(() -> {
                    assertEquals(VerityConversationManager.Submission.ACCEPTED, m.submit(session, UUID.randomUUID(),
                            "gib mir bitte 8 Diamanten", Visibility.PRIVATE, NOW, true)); return null;
                });
            }
            for (var result : executor.invokeAll(jobs)) result.get();
        }
        var results = m.drain(NOW, 16, players::get); assertEquals(8, results.size());
        for (var d : results) {
            assertEquals(d.session().player(), d.response().recipient());
            assertEquals(d.session().player(), d.response().grants().getFirst().player());
            assertEquals(1, e.pendingGrants(d.session().player()).size());
        }
    }
    @Test void persistenceRoundTripKeepsIdentityRewardsAndReplayProtection() throws IOException {
        var e = engine(); e.join(alex, NOW); e.join(max, NOW);
        var r = req(alex, "gib mir bitte 8 Diamanten", Visibility.PRIVATE); e.answer(r, alex, NOW);
        e.died(max, UUID.randomUUID(), NOW); e.setPhase(alex, Phase.CORRUPTED);
        var store = new VerityStateStore(temp.resolve("world/state.dat")); store.save(e.snapshot());
        var restored = new VerityEngine(new VerityWorldState(store.load().orElseThrow()), e.config(), catalog());
        assertEquals(e.snapshot(), restored.snapshot()); assertEquals(DUPLICATE, restored.answer(r, alex, NOW).status());
        assertEquals(2, restored.join(max, NOW).size());
        assertEquals(3, restored.respawn(max, NOW).size());
    }
    @Test void corruptOrTruncatedSaveNeverSilentlyResetsWorld() throws IOException {
        var path = temp.resolve("state.dat"); var store = new VerityStateStore(path);
        assertTrue(store.load().isEmpty()); store.save(engine().snapshot()); byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1; Files.write(path, bytes);
        assertThrows(IOException.class, store::load);
        Files.write(path, new byte[]{1, 2, 3}); assertThrows(IOException.class, store::load);
    }
    @Test void repeatedAtomicSaveLoadsLatestState() throws IOException {
        var e = engine(); var store = new VerityStateStore(temp.resolve("state.dat"));
        store.save(e.snapshot()); e.setMood(alex, 12); store.save(e.snapshot());
        assertEquals(12, store.load().orElseThrow().mood());
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
    }
    @Test void saveQueueCoalescesBacklogWithoutLosingLatestState() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var writes = new java.util.concurrent.CopyOnWriteArrayList<Snapshot>();
        var e = engine();
        try (var queue = new VeritySaveQueue(snapshot -> {
            if (writes.isEmpty()) {
                started.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test barrier timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            writes.add(snapshot);
        })) {
            var first = queue.save(e.snapshot()); assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> next = null;
            for (int i = 0; i < 100; i++) { e.setMood(alex, i); next = queue.save(e.snapshot()); }
            assertFalse(first.isDone()); assertFalse(next.isDone()); release.countDown();
            next.get(5, TimeUnit.SECONDS); assertEquals(2, writes.size()); assertEquals(99, writes.getLast().mood());
        } finally { release.countDown(); }
    }
    @Test void saveQueuePropagatesDiskFailureInsteadOfClaimingDurability() {
        var queue = new VeritySaveQueue(snapshot -> { throw new IOException("Disk full"); });
        var future = queue.save(engine().snapshot());
        assertThrows(CompletionException.class, future::join);
        assertThrows(CompletionException.class, () -> queue.save(engine().snapshot()).join());
        assertThrows(CompletionException.class, queue::close);
    }
    @Test void votingIsUuidBoundAndUsesHostTieBreak() {
        var vote = new VerityGroupDecision(Set.of(alex.uuid(), max.uuid()), alex.uuid(), NOW + 100);
        assertTrue(vote.vote(alex, true, NOW)); assertEquals(VerityGroupDecision.Outcome.OPEN, vote.outcome(NOW));
        assertTrue(vote.vote(max, false, NOW)); assertEquals(VerityGroupDecision.Outcome.YES, vote.outcome(NOW));
        assertFalse(vote.vote(max, true, NOW));
    }
    @Test void lateJoinAndLateVotesCannotAlterDecision() {
        var vote = new VerityGroupDecision(Set.of(alex.uuid()), alex.uuid(), NOW + 100);
        assertFalse(vote.vote(max, true, NOW)); assertFalse(vote.vote(alex, true, NOW + 100));
        assertEquals(VerityGroupDecision.Outcome.TIED, vote.outcome(NOW + 100));
    }
}
