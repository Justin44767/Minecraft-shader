package verity.server;

import com.mojang.brigadier.arguments.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import verity.VerityMod;
import verity.book.VerityWarningBookManager;
import verity.config.VerityServerConfig;
import verity.core.*;
import verity.core.VerityWorldState.*;
import verity.net.VerityPayload;
import verity.box.VerityBoxBlock;
import verity.box.VerityBoxBlockEntity;
import verity.entity.VerityEntity;

public final class VerityServerRuntime implements AutoCloseable {
    public record MicrophonePermit(VerityConversationManager.Session connection, UUID activation) { }
    private record Kick(VerityEngine.DeathPlan plan, VerityConversationManager.Session session, CompletableFuture<Void> saved) { }
    private record Flicker(ItemStack stack, int until) { }
    private final MinecraftServer server;
    private final VerityServerConfig config;
    private final VerityEngine engine;
    private final VerityConversationManager conversations;
    private final VerityStateStore store;
    private final Map<UUID, VerityConversationManager.Session> sessions = new HashMap<>();
    private final ConcurrentMap<UUID, MicrophonePermit> microphones = new ConcurrentHashMap<>();
    private final Map<UUID, Kick> kicks = new HashMap<>();
    private final VeritySaveQueue storage;
    private final verity.story.VerityStoryDirector director;
    private final Map<UUID, Flicker> flickers = new HashMap<>();
    private CompletableFuture<Void> lastSave = CompletableFuture.completedFuture(null);
    private boolean storageFailed;
    private UUID currentSpeaker;
    private int attentionUntil;

    public VerityServerRuntime(MinecraftServer server) {
        this.server = server;
        try {
            config = VerityServerConfig.load(FabricLoader.getInstance().getConfigDir().resolve("verity-server.properties"));
            Path path = server.getWorldPath(LevelResource.ROOT).resolve("verity/state.dat");
            store = new VerityStateStore(path);
            var state = store.load().map(VerityWorldState::new).orElseGet(() -> new VerityWorldState(UUID.randomUUID()));
            Set<String> ids = new HashSet<>(); BuiltInRegistries.ITEM.keySet().forEach(id -> ids.add(id.toString()));
            Map<String, String> aliases = new HashMap<>(ItemCatalog.germanEnglishAliases());
            aliases.entrySet().removeIf(e -> !ids.contains(e.getValue()));
            engine = new VerityEngine(state, config.core(), new ItemCatalog(ids, aliases));
            conversations = new VerityConversationManager(engine);
            storage = new VeritySaveQueue(store::save);
            director = new verity.story.VerityStoryDirector(server, this, config);
            VerityMod.LOGGER.info("VERITY 0.2.0-dev box adventure loaded; encounter={}, phase={}", engine.encounter().stage(), engine.worldView().phase());
        } catch (IOException e) {
            throw new IllegalStateException("VERITY refuses to reset invalid world state/configuration", e);
        }
    }

    public VerityEngine engine() { return engine; }
    public VerityEntity activeEntity() {
        UUID id = engine.worldView().entityId();
        if (id == null) return null;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof VerityEntity verity && !entity.isRemoved()) return verity;
        }
        return null;
    }
    public boolean demonDamage() { return config.demonDamage(); }
    public verity.story.VerityStoryDirector director() { return director; }
    public void flicker(ServerPlayer p, int duration) {
        var stack = p.getMainHandItem().is(VerityMod.FLASHLIGHT) ? p.getMainHandItem() : p.getOffhandItem();
        if (stack.is(VerityMod.FLASHLIGHT)) flickers.put(p.getUUID(), new Flicker(stack, server.getTickCount() + duration));
    }
    public ServerPlayer currentSpeaker() {
        return currentSpeaker == null || server.getTickCount() > attentionUntil ? null : server.getPlayerList().getPlayer(currentSpeaker);
    }
    public void attacked(ServerPlayer p) {
        if (storageFailed) return;
        engine.attackedBy(identity(p)); save();
        for (var recipient : server.getPlayerList().getPlayers()) sync(recipient, "", UUID.randomUUID());
    }
    public PlayerIdentity identity(ServerPlayer player) {
        boolean host = server.isSingleplayerOwner(new NameAndId(player.getGameProfile()))
                || player.getUUID().equals(config.dedicatedHost());
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        return new PlayerIdentity(player.getUUID(), player.getGameProfile().name(), host, operator,
                PlayerIdentity.Mode.valueOf(player.gameMode.getGameModeForPlayer().name()));
    }
    public void join(ServerPlayer p) {
        sessions.put(p.getUUID(), conversations.connect(p.getUUID()));
        engine.join(identity(p), now()); save(); sync(p, "", UUID.randomUUID());
        p.sendSystemMessage(Component.literal("VERITY V5 · Öffne die versiegelte Box. /verity help · Einstellungen: RIGHT SHIFT"));
    }
    public void leave(ServerPlayer p) {
        var flicker = flickers.remove(p.getUUID());
        if (flicker != null) CustomData.update(DataComponents.CUSTOM_DATA, flicker.stack(), tag -> tag.putBoolean("verity_flicker", false));
        microphones.remove(p.getUUID());
        conversations.disconnect(p.getUUID()); sessions.remove(p.getUUID()); kicks.remove(p.getUUID()); save();
    }
    public MicrophonePermit microphoneSession(UUID player) { return microphones.get(player); }
    public void disableMicrophone(UUID player) {
        microphones.remove(player);
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(player);
            if (p != null) { p.sendSystemMessage(Component.literal("VERITY: Mikrofonverarbeitung gestoppt. Textchat bleibt verfügbar.")); sync(p, "", UUID.randomUUID()); }
        });
    }
    public void voiceTranscript(MicrophonePermit permit, String text) {
        server.execute(() -> {
            var session = permit.connection();
            if (!permit.equals(microphones.get(session.player())) || !session.equals(sessions.get(session.player()))) return;
            var p = server.getPlayerList().getPlayer(session.player());
            if (p != null) chat(p, text, false, true);
        });
    }
    private int microphone(ServerPlayer p, boolean enable) {
        if (!enable) { disableMicrophone(p.getUUID()); return 1; }
        Path model = FabricLoader.getInstance().getConfigDir().resolve("verity/models/current");
        if (!FabricLoader.getInstance().isModLoaded("voicechat") || !Files.isDirectory(model.resolve("am")) || !Files.isDirectory(model.resolve("conf"))) {
            p.sendSystemMessage(Component.literal("VERITY: Simple Voice Chat oder Offline-Modell fehlt. Siehe README; Textchat bleibt verfügbar.")); return 0;
        }
        var session = sessions.get(p.getUUID());
        if (session == null) return 0;
        // Every opt-in gets a fresh generation; queued audio from before an opt-out cannot
        // become authorized again merely because the same connection opts in a second time.
        microphones.put(p.getUUID(), new MicrophonePermit(session, UUID.randomUUID()));
        p.sendSystemMessage(Component.literal("VERITY MIC ON · SVC-PTT-Audio wird auf dem Host offline erkannt. Nur mit 'Verity' ansprechen. Transkripte können gemäß Host-Regel gespeichert werden. Stop: /verity microphone disable"));
        sync(p, "", UUID.randomUUID()); return 1;
    }
    public int chat(ServerPlayer p, String text, boolean explicit, boolean privateReply) {
        if (storageFailed) { p.sendSystemMessage(Component.literal("VERITY: Speicherfehler; Aktionen sind gesperrt. Siehe Serverlog.")); return 0; }
        if (engine.encounter().stage() != EncounterStage.AWAKE) {
            if (explicit || new VerityDialogEngine().addressed(text))
                p.sendOverlayMessage(Component.literal("Aus der Box kommt ein leises Klopfen. Öffne zuerst das Siegel."));
            return 0;
        }
        var result = conversations.submit(sessions.get(p.getUUID()), UUID.randomUUID(), text,
                privateReply ? Visibility.PRIVATE : Visibility.GROUP, now(), explicit);
        if (result != VerityConversationManager.Submission.ACCEPTED && result != VerityConversationManager.Submission.NOT_ADDRESSED)
            p.sendSystemMessage(Component.literal("VERITY: " + result));
        return result == VerityConversationManager.Submission.ACCEPTED ? 1 : 0;
    }
    public void died(ServerPlayer p) {
        if (storageFailed) return;
        var plan = engine.died(identity(p), UUID.randomUUID(), now());
        var saved = save();
        if (plan.kick()) kicks.put(p.getUUID(), new Kick(plan, sessions.get(p.getUUID()), saved));
        sync(p, "Du bist gestorben. Ich beobachte dich noch immer.", UUID.randomUUID());
    }
    public void respawn(ServerPlayer p) { if (!storageFailed) { engine.respawn(identity(p), now()); save(); } }

    public void tick() {
        if (storageFailed) return;
        tickEncounter();
        director.tick();
        flickers.entrySet().removeIf(entry -> {
            var effect = entry.getValue(); boolean ended = server.getTickCount() >= effect.until();
            if (ended || server.getTickCount() % 4 == 0) CustomData.update(DataComponents.CUSTOM_DATA, effect.stack(),
                    tag -> tag.putBoolean("verity_flicker", !ended && server.getTickCount() % 12 < 5));
            return ended;
        });
        long now = now();
        boolean changed = false;
        var active = activeEntity();
        var deliveries = active != null && active.speechLeft() > 0 ? List.<VerityConversationManager.Delivery>of() : conversations.drain(now, 8, id -> {
            var p = server.getPlayerList().getPlayer(id); return p == null ? null : identity(p);
        });
        for (var delivery : deliveries) {
            var r = delivery.response();
            var p = server.getPlayerList().getPlayer(r.recipient());
            if (p == null || r.text().isEmpty()) continue;
            currentSpeaker = p.getUUID(); attentionUntil = server.getTickCount() + 100;
            List<ServerPlayer> recipients = r.visibility() == Visibility.PRIVATE ? List.of(p)
                    : server.getPlayerList().getPlayers().stream().filter(other -> other.level() == p.level()
                            && other.distanceToSqr(p) <= 32 * 32).toList();
            for (var recipient : recipients) {
                if (!ServerPlayNetworking.canSend(recipient, VerityPayload.TYPE))
                    recipient.sendSystemMessage(Component.literal("<VERITY → " + p.getGameProfile().name() + "> " + r.text()));
                sync(recipient, r.text(), r.requestId());
            }
            save();
            changed = true;
        }
        if (changed) for (var p : server.getPlayerList().getPlayers()) sync(p, "", UUID.randomUUID());
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sync(p, "", UUID.randomUUID());
                if (p.isAlive() && !lastSave.isCompletedExceptionally() && lastSave.isDone()) deliver(p);
            }
        }
        if (server.getTickCount() % 200 == 0) save();
        for (var iterator = kicks.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next(); Kick kick = entry.getValue();
            if (!Objects.equals(kick.session(), sessions.get(entry.getKey()))) { iterator.remove(); continue; }
            if (now < kick.plan().disconnectAt() || !kick.saved().isDone()) continue;
            iterator.remove();
            if (kick.saved().isCompletedExceptionally()) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                server.getPlayerList().saveAll();
                if (identity(p).host()) server.saveEverything(true, true, true);
                p.connection.disconnect(Component.literal("VERITY\nYOU DIED. VERITY IS STILL WATCHING."));
            }
        }
    }
    private void sync(ServerPlayer p, String speech, UUID requestId) {
        sync(p, speech, requestId, true);
    }
    private void sync(ServerPlayer p, String speech, UUID requestId, boolean narrate) {
        if (!ServerPlayNetworking.canSend(p, VerityPayload.TYPE)) return;
        var v = engine.view(identity(p));
        ServerPlayNetworking.send(p, new VerityPayload(requestId, speech, v.mood(), v.personalTrust(), v.phase().ordinal(), microphones.containsKey(p.getUUID()), narrate));
    }

    private void deliver(ServerPlayer player) {
        for (Grant grant : engine.pendingGrants(player.getUUID())) {
            ItemStack stack = grant.kind() == GrantKind.STARTER_BOOK ? VerityWarningBookManager.create()
                    : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(grant.itemId())), grant.amount());
            if (stack.isEmpty()) { VerityMod.LOGGER.error("Unregistered pending item {}; retaining ticket", grant.itemId()); continue; }
            String ticket = grant.id().toString();
            boolean alreadyPresent = false;
            for (int i = 0; i < 36; i++) {
                var item = player.getInventory().getItem(i);
                if (item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("verity_grant", "").equals(ticket))
                    alreadyPresent = true;
            }
            if (alreadyPresent) { engine.acknowledgeGrant(player.getUUID(), grant.id()); save(); continue; }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("verity_grant", ticket));
            if (grant.kind() == GrantKind.AWAKENING_BOX)
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("verity_world", engine.worldId().toString()));
            int capacity = 0;
            for (int i = 0; i < 36; i++) {
                var current = player.getInventory().getItem(i);
                if (current.isEmpty()) capacity += stack.getMaxStackSize();
                else if (ItemStack.isSameItemSameComponents(current, stack)) capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
            }
            if (capacity < stack.getCount()) continue;
            if (player.getInventory().add(stack)) {
                // Persist the inventory before retiring the world ticket. A saved ticket marker
                // is used to reconcile the preceding delivery after an interrupted shutdown.
                server.getPlayerList().saveAll();
                engine.acknowledgeGrant(player.getUUID(), grant.id()); save();
                player.sendSystemMessage(Component.literal(grant.kind() == GrantKind.AWAKENING_BOX
                        ? "Ein versiegeltes Paket ist angekommen. Stelle die VERITY-Box ab und öffne sie mit Rechtsklick."
                        : "VERITY: Übergabe abgeschlossen."));
            }
        }
    }
    private CompletableFuture<Void> save() {
        if (storageFailed) return CompletableFuture.failedFuture(new IOException("Storage disabled after failure"));
        lastSave = storage.save(engine.snapshot());
        lastSave.whenComplete((ignored, error) -> {
            if (error != null) server.execute(() -> {
                storageFailed = true;
                VerityMod.LOGGER.error("VERITY state could not be saved; further state changes disabled", error);
            });
        });
        return lastSave;
    }
    @Override public void close() {
        microphones.clear();
        flickers.values().forEach(effect -> CustomData.update(DataComponents.CUSTOM_DATA, effect.stack(), tag -> tag.putBoolean("verity_flicker", false)));
        flickers.clear();
        try { save().join(); }
        finally { storage.close(); }
    }
    private static long now() { return System.currentTimeMillis(); }

    public boolean openBox(ServerPlayer player, BlockPos pos) {
        if (storageFailed || player.isSpectator() || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return false;
        var level = player.level();
        if (!(level.getBlockEntity(pos) instanceof VerityBoxBlockEntity box) || !box.belongsTo(engine.worldId())) return false;
        if (engine.encounter().stage() != EncounterStage.SEALED) {
            player.sendOverlayMessage(Component.literal(engine.encounter().stage() == EncounterStage.OPENING
                    ? "Das Siegel bricht bereits …" : "Die Box ist leer. VERITY ist schon frei.")); return false;
        }
        if (level.getBlockState(pos).getValue(VerityBoxBlock.OPEN)) {
            player.sendOverlayMessage(Component.literal("Diese Box ist schon geöffnet. Nimm das versiegelte Paket aus deinem Inventar.")); return false;
        }
        if (!level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.above(2)).isAir()) {
            player.sendOverlayMessage(Component.literal("VERITY braucht zwei freie Blöcke über seiner Box.")); return false;
        }
        var point = new WorldPoint(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
        if (!engine.beginUnboxing(identity(player), point)) return false;
        level.setBlock(pos, level.getBlockState(pos).setValue(VerityBoxBlock.OPEN, true), 3);
        box.progress(0);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, .6f, .65f);
        save(); return true;
    }
    private void tickEncounter() {
        var encounter = engine.encounter();
        if (encounter.stage() != EncounterStage.OPENING || encounter.box() == null) return;
        var point = encounter.box();
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())));
        BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
        if (level == null || !level.hasChunkAt(pos)) return;
        if (!(level.getBlockEntity(pos) instanceof VerityBoxBlockEntity box) || !box.belongsTo(engine.worldId())) {
            engine.cancelUnboxing(now()); save(); return;
        }
        int elapsed = engine.tickUnboxing();
        if (elapsed % 5 == 0) {
            box.progress(elapsed);
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 5, .35, .35, .35, .08);
        }
        if (elapsed == 20 || elapsed == 40) level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .8f, .6f + elapsed / 50f);
        if (elapsed < 60) return;
        var id = engine.reserveAwakening();
        Entity existing = level.getEntity(id);
        VerityEntity entity;
        if (existing instanceof VerityEntity verity) entity = verity;
        else {
            entity = VerityMod.ENTITY.create(level, EntitySpawnReason.TRIGGERED);
            if (entity == null) return;
            entity.setUUID(id);
            entity.snapTo(pos.getX() + .5, pos.getY() + 1.15, pos.getZ() + .5, 0, 0);
            if (!level.noCollision(entity, entity.getBoundingBox())) return;
            entity.reveal();
            if (!level.addFreshEntity(entity)) return;
        }
        if (engine.finishAwakening(id)) {
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + .4, entity.getZ(), 40, .4, .5, .4, .08);
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, .5f, .65f);
            currentSpeaker = encounter.opener(); attentionUntil = server.getTickCount() + 160;
            playVoice(entity, 1);
            save();
        }
    }
    public void publicSpeech(ServerLevel level, BlockPos pos, String text) {
        publicSpeech(level, pos, text, true);
    }
    public void playVoice(VerityEntity entity, int clipId) {
        if (!(entity.level() instanceof ServerLevel level) || entity.speechLeft() > 0) return;
        var clip = verity.voice.VerityVoiceClips.clip(clipId);
        entity.speak(clipId, clip.ticks());
        level.playSound(null, entity.blockPosition(), verity.voice.VerityVoiceClips.sound(clipId), SoundSource.VOICE, .9f, 1);
        publicSpeech(level, entity.blockPosition(), clip.text(), false);
    }
    private void stopVoice(VerityEntity entity) {
        if (entity.speechClip() <= 0) return;
        var sound = verity.voice.VerityVoiceClips.sound(entity.speechClip());
        for (var p : server.getPlayerList().getPlayers()) if (p.level() == entity.level())
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(sound.location(), SoundSource.VOICE));
        entity.speak(0, 0);
    }
    private void publicSpeech(ServerLevel level, BlockPos pos, String text, boolean narrate) {
        UUID requestId = UUID.randomUUID();
        for (var p : server.getPlayerList().getPlayers()) {
            if (p.level() != level || p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 48 * 48) continue;
            if (!ServerPlayNetworking.canSend(p, VerityPayload.TYPE)) p.sendSystemMessage(Component.literal("<VERITY> " + text));
            sync(p, text, requestId, narrate);
        }
    }
    private int giveBox(ServerPlayer player) {
        if (engine.encounter().stage() != EncounterStage.SEALED) {
            player.sendSystemMessage(Component.literal("VERITY ist bereits frei. Mit /verity pack kannst du ihn wieder einpacken.")); return 0;
        }
        engine.reissueEquipment(identity(player), GrantKind.AWAKENING_BOX, now()); save(); return 1;
    }
    public int pack(ServerPlayer player) {
        if (storageFailed || !identity(player).administrator() || engine.encounter().stage() != EncounterStage.AWAKE) return 0;
        var id = engine.worldView().entityId();
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity instanceof VerityEntity verity) {
                engine.packIntoBox(identity(player), now());
                stopVoice(verity);
                entity.discard(); save();
                player.sendSystemMessage(Component.literal("VERITY wartet wieder in seiner versiegelten Box. Seine Erinnerungen bleiben erhalten.")); return 1;
            }
        }
        player.sendSystemMessage(Component.literal("Komm näher zu VERITY, damit sein Chunk geladen ist.")); return 0;
    }

    private int spawn(ServerPlayer player) {
        PlayerIdentity actor = identity(player);
        if (!actor.administrator()) return 0;
        var snapshot = engine.snapshot();
        if (snapshot.entityId() != null) {
            player.sendSystemMessage(Component.literal("VERITY ist bereits registriert. Geladene und ungeladene Chunks teilen dieselbe Entity-UUID.")); return 0;
        }
        var entity = VerityMod.ENTITY.create(player.level(), EntitySpawnReason.COMMAND);
        if (entity == null) return 0;
        var position = player.position().add(player.getLookAngle().scale(2)).add(0, 1, 0);
        entity.snapTo(position.x, position.y, position.z, player.getYRot() + 180, 0);
        engine.claimEntity(actor, entity.getUUID());
        if (!player.level().addFreshEntity(entity)) { engine.releaseEntity(actor, entity.getUUID()); return 0; }
        save(); return 1;
    }
    private int remove(ServerPlayer player) {
        var id = engine.snapshot().entityId();
        if (id == null) return 0;
        for (var level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) { e.discard(); engine.releaseEntity(identity(player), id); save(); return 1; }
        }
        player.sendSystemMessage(Component.literal("VERITYs Chunk ist nicht geladen. Kein Ersatz wird erzeugt, um Duplikate zu vermeiden.")); return 0;
    }
    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = Commands.literal("verity").executes(ctx -> help(ctx.getSource().getPlayerOrException()));
            root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource().getPlayerOrException())));
            root.then(Commands.literal("ask").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException(); return VerityMod.runtime(ctx.getSource().getServer()).chat(p, StringArgumentType.getString(ctx, "message"), true, false);
            })));
            root.then(Commands.literal("private").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException(); return VerityMod.runtime(ctx.getSource().getServer()).chat(p, StringArgumentType.getString(ctx, "message"), true, true);
            })));
            root.then(Commands.literal("forget").executes(ctx -> {
                var r = VerityMod.runtime(ctx.getSource().getServer()); var p = ctx.getSource().getPlayerOrException();
                r.engine.forget(r.identity(p)); r.save(); p.sendSystemMessage(Component.literal("VERITY: Dein Gesprächsverlauf wurde gelöscht.")); return 1;
            }));
            root.then(Commands.literal("microphone")
                    .then(Commands.literal("enable").executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).microphone(ctx.getSource().getPlayerOrException(), true)))
                    .then(Commands.literal("disable").executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).microphone(ctx.getSource().getPlayerOrException(), false))));
            root.then(Commands.literal("mood").executes(ctx -> {
                var r = VerityMod.runtime(ctx.getSource().getServer()); var p = ctx.getSource().getPlayerOrException(); var v = r.engine.view(r.identity(p));
                p.sendSystemMessage(Component.literal("VERITY MOOD: " + v.mood() + "% · PERSONAL TRUST: " + v.personalTrust() + "% · " + v.phase())); return 1;
            }).then(Commands.literal("set").requires(VerityServerRuntime::administrator)
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                        var r = VerityMod.runtime(ctx.getSource().getServer()); r.engine.setMood(r.identity(ctx.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(ctx, "value")); r.save(); return 1;
                    }))));
            for (String cmd : List.of("follow", "stop")) root.then(Commands.literal(cmd).executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException(); return VerityMod.runtime(ctx.getSource().getServer()).chat(p, cmd.equals("follow") ? "folge mir" : "bleib hier", true, false);
            }));
            root.then(Commands.literal("spawn").requires(VerityServerRuntime::administrator)
                    .executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).spawn(ctx.getSource().getPlayerOrException())));
            root.then(Commands.literal("remove").requires(VerityServerRuntime::administrator)
                    .executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).remove(ctx.getSource().getPlayerOrException())));
            root.then(Commands.literal("box").requires(VerityServerRuntime::administrator)
                    .executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).giveBox(ctx.getSource().getPlayerOrException())));
            root.then(Commands.literal("pack").requires(VerityServerRuntime::administrator)
                    .executes(ctx -> VerityMod.runtime(ctx.getSource().getServer()).pack(ctx.getSource().getPlayerOrException())));
            root.then(Commands.literal("settings").executes(ctx -> {
                ctx.getSource().getPlayerOrException().sendSystemMessage(Component.literal("RIGHT SHIFT: lokale Anzeige/Sprache. Host-Regeln: config/verity-server.properties (Welt neu starten).")); return 1;
            }));
            for (String cmd : List.of("book", "flashlight")) root.then(Commands.literal(cmd)
                    .requires(VerityServerRuntime::administrator).executes(ctx -> {
                        var r = VerityMod.runtime(ctx.getSource().getServer());
                        var p = ctx.getSource().getPlayerOrException();
                        r.engine.reissueEquipment(r.identity(p), cmd.equals("book") ? GrantKind.STARTER_BOOK : GrantKind.STARTER_FLASHLIGHT, now());
                        r.save(); return 1;
                    }));
            root.then(Commands.literal("voice").then(Commands.literal("test").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException(); var r = VerityMod.runtime(ctx.getSource().getServer());
                r.sync(p, "Hallo, " + p.getGameProfile().name() + ". Ich bin VERITY. Dies ist die lokale Sprachausgabe.", UUID.randomUUID()); return 1;
            })));
            root.then(Commands.literal("phase").executes(ctx -> {
                var r = VerityMod.runtime(ctx.getSource().getServer());
                ctx.getSource().getPlayerOrException().sendSystemMessage(Component.literal("VERITY PHASE: " + r.engine.snapshot().phase())); return 1;
            }));
            dispatcher.register(root);
        });
    }
    private static int help(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("VERITY · Box abstellen und rechtsklicken · ask <Text> · private <Text> · mood · follow · stop · forget · settings · phase · voice test · microphone enable|disable · Host/Operator: box, pack, book, flashlight, mood set <0–100> (jeweils /verity davor)"));
        return 1;
    }
    private static boolean administrator(net.minecraft.commands.CommandSourceStack source) {
        // Minecraft 26.3 probes command requirements with a compilation source that has
        // neither server nor entity while serializing the command tree during login.
        // Such probes carry no player authority, regardless of their permission set.
        if (!(source.getEntity() instanceof ServerPlayer p) || source.getServer() == null) return false;
        var r = VerityMod.runtime(source.getServer());
        return r != null && !r.storageFailed && r.identity(p).administrator();
    }
}
