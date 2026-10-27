package verity.box;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import verity.VerityMod;

public final class VerityBoxBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public VerityBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, OPEN); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(1, 0, 1, 15, 13, 15);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new VerityBoxBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? createTickerHelper(type, VerityMod.BOX_ENTITY, (l, p, s, box) -> box.clientTick()) : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VerityBoxBlockEntity box) {
            var runtime = VerityMod.runtime(level.getServer());
            if (runtime == null) return;
            String source = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("verity_world", "");
            if (source.isEmpty() || source.equals(runtime.engine().worldId().toString())) box.bind(runtime.engine().worldId());
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        var runtime = VerityMod.runtime(level.getServer());
        if (runtime == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && !state.getValue(OPEN) && runtime.engine().encounter().stage() != verity.core.VerityWorldState.EncounterStage.OPENING) {
            ItemStack stack = new ItemStack(VerityMod.BOX_ITEM);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("verity_world", runtime.engine().worldId().toString()));
            if (player.getInventory().add(stack)) level.removeBlock(pos, false);
            else player.sendOverlayMessage(Component.literal("Schaffe einen freien Inventarplatz für die Box."));
            return InteractionResult.SUCCESS;
        }
        runtime.openBox(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
}
