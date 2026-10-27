package verity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import verity.box.VerityBoxBlock;
import verity.box.VerityBoxBlockEntity;
import org.slf4j.*;
import verity.entity.VerityEntity;
import verity.items.VerityFlashlightItem;
import verity.net.VerityPayload;
import verity.server.VerityServerRuntime;

public final class VerityMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("verity");
    public static final Identifier ENTITY_ID = id("verity");
    public static final EntityType<VerityEntity> ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, ENTITY_ID,
            EntityType.Builder.of(VerityEntity::new, MobCategory.CREATURE).sized(0.9f, 0.9f).clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ENTITY_ID)));
    public static final Item FLASHLIGHT = Registry.register(BuiltInRegistries.ITEM, id("flashlight"),
            new VerityFlashlightItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("flashlight"))).stacksTo(1)));
    public static final Block BOX = Registry.register(BuiltInRegistries.BLOCK, id("sealed_box"),
            new VerityBoxBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("sealed_box")))
                    .strength(-1, 3600000).noOcclusion().noLootTable().sound(SoundType.WOOD)
                    .lightLevel(state -> state.getValue(VerityBoxBlock.OPEN) ? 7 : 2)));
    public static final Item BOX_ITEM = Registry.register(BuiltInRegistries.ITEM, id("sealed_box"),
            new BlockItem(BOX, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("sealed_box"))).stacksTo(1)));
    public static final BlockEntityType<VerityBoxBlockEntity> BOX_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("sealed_box"), new BlockEntityType<>(VerityBoxBlockEntity::new, Set.of(BOX)));
    private static final Map<MinecraftServer, VerityServerRuntime> RUNTIMES = new ConcurrentHashMap<>();
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("verity", path); }
    public static VerityServerRuntime runtime(MinecraftServer server) {
        return server == null ? null : RUNTIMES.get(server);
    }
    @Override public void onInitialize() {
        verity.voice.VerityVoiceClips.register();
        FabricDefaultAttributeRegistry.register(ENTITY, Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100)
                .add(Attributes.FLYING_SPEED, 0.5).add(Attributes.MOVEMENT_SPEED, 0.3));
        PayloadTypeRegistry.clientboundPlay().register(VerityPayload.TYPE, VerityPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> RUNTIMES.put(server, new VerityServerRuntime(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var r = RUNTIMES.remove(server); if (r != null) r.close(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var r = runtime(server); if (r != null) r.tick(); });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            var r = runtime(level.getServer());
            if (entity instanceof VerityEntity && r != null) {
                if (r.engine().encounter().stage() == verity.core.VerityWorldState.EncounterStage.SEALED
                        && r.engine().worldView().entityId() != null) { entity.discard(); return; }
                if (!r.engine().claimLoadedEntity(entity.getUUID()).equals(entity.getUUID())) entity.discard();
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> runtime(server).join(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var r = runtime(server); if (r != null) r.leave(handler.getPlayer());
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, type) -> runtime(player.level().getServer()).chat(player, message.signedContent(), false, false));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p) runtime(p.level().getServer()).died(p);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> {
            if (!alive) runtime(player.level().getServer()).respawn(player);
        });
        VerityServerRuntime.registerCommands();
    }
}
