package verity.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import verity.box.VerityBoxBlockEntity;
import verity.core.*;
import verity.core.VerityWorldState.EncounterStage;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import verity.VerityMod;
import verity.book.VerityWarningBookManager;
import verity.items.VerityFlashlightItem;

public class VerityGameTests {
    @GameTest public void administrativeRequirementsRejectCompilationSources(GameTestHelper h) {
        var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("verity");
        for (var permissions : new PermissionSet[]{PermissionSet.NO_PERMISSIONS, PermissionSet.ALL_PERMISSIONS}) {
            var source = Commands.createCompilationContext(permissions);
            h.assertTrue(source.getServer() == null, "Minecraft restriction probe must not need a server");
            for (String command : new String[]{"spawn", "remove", "book", "flashlight", "box", "pack"}) {
                h.assertTrue(!root.getChild(command).getRequirement().test(source), "Synthetic source must not authorize " + command);
            }
            h.assertTrue(!root.getChild("mood").getChild("set").getRequirement().test(source), "Synthetic source must not authorize mood set");
        }
        h.succeed();
    }

    @GameTest(maxTicks = 100) public void operatorCanJoinAndReceiveStarterInventory(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var profile = new GameProfile(UUID.randomUUID(), "VerityJoinTest");
        var player = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault()) {
            @Override public PermissionSet permissions() { return PermissionSet.ALL_PERMISSIONS; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            // Use the real login path: it serializes administrative commands before JOIN.
            server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        } catch (RuntimeException | Error failure) {
            channel.finishAndReleaseAll();
            throw failure;
        }
        h.succeedWhen(() -> {
            h.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 1, "Join must deliver exactly one guide");
            h.assertTrue(player.getInventory().countItem(VerityMod.FLASHLIGHT) == 1, "Join must deliver exactly one flashlight");
            var commands = server.getCommands().getDispatcher().getRoot().getChild("verity");
            h.assertTrue(commands.getChild("spawn").canUse(player.createCommandSourceStack()), "Authenticated operator must retain administrative access");
            var guest = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "VerityGuest"), ClientInformation.createDefault()) {
                @Override public PermissionSet permissions() { return PermissionSet.NO_PERMISSIONS; }
            };
            for (String command : new String[]{"spawn", "remove", "book", "flashlight", "box", "pack"}) {
                h.assertTrue(!commands.getChild(command).canUse(guest.createCommandSourceStack()), "Guest must not gain administrative access to " + command);
            }
            h.assertTrue(!commands.getChild("mood").getChild("set").canUse(guest.createCommandSourceStack()), "Guest must not gain mood-set permission");
            VerityMod.runtime(server).leave(player);
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        });
    }

    @GameTest public void writtenGuideHasTenRealPages(GameTestHelper h) {
        var stack = VerityWarningBookManager.create();
        var book = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(book != null && book.pages().size() == 10, "Ten real written book pages required");
        for (var page : book.pages()) h.assertTrue(!page.raw().getString().isBlank(), "Page must not be blank");
        h.assertTrue(book.author().equals("UNKNOWN"), "Book author must be UNKNOWN"); h.succeed();
    }
    @GameTest public void flashlightComponentSurvivesCopy(GameTestHelper h) {
        var item = new ItemStack(VerityMod.FLASHLIGHT);
        h.assertTrue(!VerityFlashlightItem.enabled(item), "Flashlight should default OFF");
        CustomData.update(DataComponents.CUSTOM_DATA, item, tag -> tag.putBoolean("verity_on", true));
        h.assertTrue(VerityFlashlightItem.enabled(item.copy()), "Switch state must be stored on the physical item"); h.succeed();
    }
    @GameTest public void entityFactoryAndWorldAuthority(GameTestHelper h) {
        var entity = VerityMod.ENTITY.create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(entity != null && entity.getHealth() == 100, "Entity attributes must be registered");
        var r = VerityMod.runtime(h.getLevel().getServer());
        h.assertTrue(r != null, "Server lifecycle must initialize VERITY runtime");
        var isolated = new VerityEngine(new VerityWorldState(UUID.randomUUID()), VerityConfig.defaults(), new ItemCatalog(Set.of(), Map.of()));
        var claimed = isolated.claimLoadedEntity(entity.getUUID());
        h.assertTrue(isolated.claimLoadedEntity(UUID.randomUUID()).equals(claimed), "Second claim must not replace the shared entity");
        h.assertTrue(claimed.equals(isolated.worldView().entityId()), "Entity must have a world-wide identity"); h.succeed();
    }
    @GameTest(maxTicks = 650, skyAccess = true) public void boxAdventureRunsEventsCombatAndLightRescue(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer(); var runtime = VerityMod.runtime(server);
        var profile = new GameProfile(UUID.randomUUID(), "VerityBoxTest");
        var player = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault()) {
            @Override public PermissionSet permissions() { return PermissionSet.ALL_PERMISSIONS; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        server.getCommands().getDispatcher().execute("verity box", player.createCommandSourceStack());
        BlockPos first = h.absolutePos(new BlockPos(2, 1, 2)), second = h.absolutePos(new BlockPos(5, 1, 2));
        player.snapTo(first.getX() + .5, first.getY(), first.getZ() + 3);
        player.setNoGravity(true);
        java.util.concurrent.atomic.AtomicReference<UUID> companion = new java.util.concurrent.atomic.AtomicReference<>();
        Runnable placeSecond = () -> {
            h.getLevel().setBlock(second.below(), Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(second, VerityMod.BOX.defaultBlockState(), 3);
            VerityMod.BOX.setPlacedBy(h.getLevel(), second, h.getLevel().getBlockState(second), player, new ItemStack(VerityMod.BOX_ITEM));
        };
        h.startSequence().thenWaitUntil(() -> h.assertTrue(player.getInventory().countItem(VerityMod.BOX_ITEM) == 1,
                        "Host must receive one actual box item; count=" + player.getInventory().countItem(VerityMod.BOX_ITEM)
                                + ", alive=" + player.isAlive() + ", stage=" + runtime.engine().encounter().stage()
                                + ", tickets=" + runtime.engine().pendingGrants(player.getUUID()).size()))
                .thenExecute(() -> {
                    h.getLevel().setBlock(first.below(), Blocks.STONE.defaultBlockState(), 3);
                    h.getLevel().setBlock(first, VerityMod.BOX.defaultBlockState(), 3);
                    VerityMod.BOX.setPlacedBy(h.getLevel(), first, h.getLevel().getBlockState(first), player, new ItemStack(VerityMod.BOX_ITEM));
                    h.assertTrue(h.getLevel().getBlockEntity(first) instanceof VerityBoxBlockEntity, "A box must have its own block entity");
                    h.assertTrue(runtime.openBox(player, first), "A normal block interaction must start the encounter");
                    h.assertTrue(!runtime.openBox(player, first), "Double click must not restart the encounter");
                }).thenWaitUntil(() -> h.assertTrue(runtime.engine().encounter().stage() == EncounterStage.AWAKE, "Await completion of the real 60-tick opening"))
                .thenExecute(() -> {
                    UUID id = runtime.engine().worldView().entityId(); companion.set(id);
                    var entity = h.getLevel().getEntity(id);
                    h.assertTrue(entity instanceof verity.entity.VerityEntity && entity.isAlive(), "VERITY must be a real world entity");
                    h.assertTrue(h.getLevel().getEntities(VerityMod.ENTITY, new AABB(first).inflate(10), e -> e.isAlive()).size() == 1, "Only one companion may emerge");
                    h.assertTrue(!runtime.openBox(player, first), "An empty case must not spawn a second entity");
                    h.assertTrue(runtime.pack(player) == 1, "An authenticated host/operator must be able to pack the companion");
                    h.assertTrue(runtime.engine().encounter().stage() == EncounterStage.SEALED, "Packing must persist the sealed stage");
                    h.assertTrue(!runtime.openBox(player, first), "A spent physical case must remain empty");
                    placeSecond.run(); h.assertTrue(runtime.openBox(player, second), "A fresh sealed case can release the remembered companion");
                }).thenWaitUntil(() -> h.assertTrue(runtime.engine().encounter().stage() == EncounterStage.AWAKE, "Await second opening"))
                .thenExecute(() -> {
                    h.assertTrue(companion.get().equals(runtime.engine().worldView().entityId()), "Repacking must retain the companion UUID");
                    h.assertTrue(h.getLevel().getEntities(VerityMod.ENTITY, new AABB(second).inflate(10), e -> e.isAlive()).size() == 1, "Packing and reopening must never duplicate VERITY");
                    var entity = runtime.activeEntity();
                    var flashlight = new ItemStack(VerityMod.FLASHLIGHT);
                    CustomData.update(DataComponents.CUSTOM_DATA, flashlight, tag -> tag.putBoolean("verity_on", true));
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, flashlight);
                    for (var event : verity.story.VerityStoryDirector.Event.values()) runtime.director().trigger(event, entity, player);
                    h.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "The darkness event must affect the real player");
                    h.assertTrue(entity.isInvisible(), "The vanish event must hide the actual entity");
                    h.assertTrue(entity.glitchTicks() > 0, "Face events must update synchronized entity data");
                    // WATCHER may cross the runner's forced-chunk boundary. Return the fixture
                    // to the active test area before awaiting actual entity ticks/combat.
                    entity.snapTo(player.getX() + 1, player.getY(), player.getZ());
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setHealth(20);
                    runtime.engine().setMood(runtime.identity(player), 0);
                    runtime.engine().setPhase(runtime.identity(player), verity.core.VerityWorldState.Phase.DEMON);
                }).thenWaitUntil(() -> h.assertTrue(runtime.activeEntity().transformTicks() > 0,
                        "Demon phase must start the real transformation; phase=" + runtime.activeEntity().phase()
                                + ", entityTicks=" + runtime.activeEntity().tickCount))
                .thenExecuteFor(110, () -> {
                    // Keep the fixture in melee range; production ticks still choose whether to attack.
                    runtime.activeEntity().snapTo(player.getX() + 1, player.getY(), player.getZ());
                }).thenExecute(() -> {
                    h.assertTrue(!runtime.activeEntity().isInvisible(), "Vanish must expire without leaving a permanently invisible companion");
                    h.assertTrue(player.getHealth() < 20, "An unlit demon must damage a survival player");
                    player.setHealth(20);
                    var flashlight = new ItemStack(VerityMod.FLASHLIGHT);
                    CustomData.update(DataComponents.CUSTOM_DATA, flashlight, tag -> tag.putBoolean("verity_on", true));
                    player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, flashlight);
                }).thenExecuteFor(90, () -> {
                    var entity = runtime.activeEntity();
                    entity.snapTo(player.getX() + 1, player.getY(), player.getZ());
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, entity.position().add(0, .5, 0));
                    h.assertTrue(player.getHealth() == 20, "An aimed flashlight must prevent demon melee damage");
                }).thenExecute(() -> {
                    h.assertTrue(runtime.engine().worldView().mood() >= 4, "Illuminating the demon must perform real mood recovery");
                    runtime.leave(player); server.getPlayerList().remove(player); channel.finishAndReleaseAll();
                }).thenSucceed();
    }
}
