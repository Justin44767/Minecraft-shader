package verity.client;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
public final class VerityRenderState extends LivingEntityRenderState {
    public int mood = 70, phase;
    public float reveal = 1, speechAmplitude, transformation = 1;
    public boolean speaking, glitch;
}
