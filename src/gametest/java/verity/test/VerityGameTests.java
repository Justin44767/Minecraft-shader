package verity.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
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
            for (String command : new String[]{"spawn", "remove", "book", "flashlight"}) {
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
            for (String command : new String[]{"spawn", "remove", "book", "flashlight"}) {
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
        var claimed = r.engine().claimLoadedEntity(entity.getUUID());
        h.assertTrue(r.engine().claimLoadedEntity(java.util.UUID.randomUUID()).equals(claimed), "Second claim must not replace the shared entity");
        h.assertTrue(claimed.equals(r.engine().worldView().entityId()), "Entity must have a world-wide identity"); h.succeed();
    }
}
