package verity.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import verity.VerityMod;
import verity.book.VerityWarningBookManager;
import verity.items.VerityFlashlightItem;

public class VerityGameTests {
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
