package verity.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public final class VerityBoxRenderState extends BlockEntityRenderState {
    public float opening;
    public Direction facing = Direction.NORTH;
}
