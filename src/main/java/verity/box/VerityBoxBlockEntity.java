package verity.box;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import verity.VerityMod;

/** Real persistent block entity; progress is authored by the server and interpolated on clients. */
public final class VerityBoxBlockEntity extends BlockEntity {
    private UUID worldId;
    private float progress, previousProgress;
    public VerityBoxBlockEntity(BlockPos pos, BlockState state) { super(VerityMod.BOX_ENTITY, pos, state); }
    public void bind(UUID worldId) { this.worldId = worldId; changed(); }
    public boolean belongsTo(UUID id) { return id.equals(worldId); }
    public void progress(int ticks) {
        progress = Math.clamp(ticks / 60f, 0, 1); previousProgress = progress;
        changed();
    }
    public void clientTick() {
        previousProgress = progress;
        if (getBlockState().getValue(VerityBoxBlock.OPEN)) progress = Math.min(1, progress + 1f / 60);
    }
    public float opening(float delta) {
        float linear = previousProgress + (progress - previousProgress) * delta;
        return (float)(1 - Math.pow(1 - linear, 3));
    }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (worldId != null) out.putString("verityWorld", worldId.toString());
        out.putFloat("opening", progress);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        String id = in.getStringOr("verityWorld", "");
        try { worldId = id.isEmpty() ? null : UUID.fromString(id); }
        catch (IllegalArgumentException invalid) { worldId = null; }
        progress = Math.clamp(in.getFloatOr("opening", 0), 0, 1); previousProgress = progress;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (worldId != null) tag.putString("verityWorld", worldId.toString());
        tag.putFloat("opening", progress);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
