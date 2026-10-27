package verity.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;

/** Physical custom item and synchronized switch. Lighting integration is tracked separately. */
public final class VerityFlashlightItem extends Item {
    public VerityFlashlightItem(Properties properties) { super(properties); }
    public static boolean enabled(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("verity_on", false);
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            boolean on = !enabled(stack);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("verity_on", on));
            player.sendOverlayMessage(Component.literal("VERITY FLASHLIGHT: " + (on ? "ON" : "OFF") + " · Lichtintegration noch in Entwicklung"));
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.3f, on ? 1.2f : 0.8f);
        }
        return InteractionResult.SUCCESS;
    }
}
