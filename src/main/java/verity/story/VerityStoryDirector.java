package verity.story;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import verity.config.VerityServerConfig;
import verity.core.VerityWorldState.Phase;
import verity.entity.VerityEntity;
import verity.server.VerityServerRuntime;

/** A shared, non-destructive horror timeline driven by server play time and personal interaction. */
public final class VerityStoryDirector {
    public enum Event { VANISH, BEHIND_YOU, RED_EYES, FOOTSTEPS, WHISPER, FLICKER, SOUL_TRAIL,
        MOOD_DROP, FACE_GLITCH, DARKNESS, MESSAGE, DISTANT_THUNDER, CAVE_ECHO, WATCHER,
        SHUDDER, COLD_BREATH, DARK_SPIRAL, HEARTBEAT, WARNING_VOICE, WARNING_TITLE }
    private record Step(long tick, ServerLevel level, BlockPos pos) { }
    private final MinecraftServer server;
    private final VerityServerRuntime runtime;
    private final VerityServerConfig config;
    private final List<Step> steps = new ArrayList<>();
    private int pendingPhaseVoice;
    public VerityStoryDirector(MinecraftServer server, VerityServerRuntime runtime, VerityServerConfig config) {
        this.server = server; this.runtime = runtime; this.config = config;
    }
    public void tick() {
        steps.removeIf(s -> {
            if (server.getTickCount() < s.tick()) return false;
            s.level().playSound(null, s.pos(), SoundEvents.DEEPSLATE_STEP, SoundSource.AMBIENT, .7f, .65f); return true;
        });
        if (!config.storyEnabled() || server.getTickCount() % 20 != 0) return;
        var entity = runtime.activeEntity();
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
        var nearby = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(entity) < 64 * 64).toList();
        if (nearby.isEmpty()) return;
        var before = runtime.engine().worldView().phase();
        var after = runtime.engine().advanceStory(20, config.chapterSeconds() * 20L);
        long time = runtime.engine().storyTime();
        if (after != before) {
            pendingPhaseVoice = after == Phase.DEMON ? 5 : after == Phase.CORRUPTED ? 4 : before == Phase.DEMON || before == Phase.CORRUPTED ? 7 : 3;
        }
        if (pendingPhaseVoice > 0) {
            if (entity.speechLeft() == 0) { runtime.playVoice(entity, pendingPhaseVoice); pendingPhaseVoice = 0; }
            return;
        }
        long interval = config.horrorSeconds() * 20L;
        if (after == Phase.FRIEND) {
            if (time > 0 && time % (interval * 2) == 0) runtime.playVoice(entity, 2);
            return;
        }
        if (time > 0 && time % interval == 0) {
            int ordinal = (int)((time / interval - 1) % Event.values().length);
            var player = nearby.get((int)((time / interval) % nearby.size()));
            trigger(Event.values()[ordinal], entity, player);
        }
    }
    public void trigger(Event event, VerityEntity entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel level) || player.level() != level) return;
        Vec3 eye = player.getEyePosition(), behind = player.position().subtract(player.getLookAngle().multiply(3, 0, 3));
        Vec3 ahead = eye.add(player.getLookAngle().scale(6));
        BlockPos origin = player.blockPosition();
        switch (event) {
            case VANISH -> entity.hideFor(60);
            case BEHIND_YOU -> entity.appearAt(behind.add(0, 1, 0));
            case RED_EYES -> {
                level.sendParticles(ParticleTypes.FLAME, ahead.x - .2, ahead.y, ahead.z, 2, .01, .01, .01, 0);
                level.sendParticles(ParticleTypes.FLAME, ahead.x + .2, ahead.y, ahead.z, 2, .01, .01, .01, 0);
            }
            case FOOTSTEPS -> {
                for (int i = 0; i < 4; i++) steps.add(new Step(server.getTickCount() + i * 7L, level, BlockPos.containing(behind.add(i * .5, 0, 0))));
            }
            case WHISPER -> { entity.hideFor(80); runtime.playVoice(entity, 3); }
            case FLICKER -> runtime.flicker(player, 80);
            case SOUL_TRAIL -> {
                for (int i = 0; i < 10; i++) {
                    var p = behind.add(player.getLookAngle().scale(i * .5));
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + .2, p.z, 2, .12, .05, .12, .01);
                }
            }
            case MOOD_DROP -> { runtime.engine().disturbMood(-7); entity.glitchFor(40); }
            case FACE_GLITCH -> entity.glitchFor(80);
            case DARKNESS -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            case MESSAGE -> runtime.publicSpeech(level, origin, "Du hast die Box geöffnet. Die Welt hat sich deinen Namen gemerkt.");
            case DISTANT_THUNDER -> level.playSound(null, origin.offset(12, 8, 8), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, .55f, .6f);
            case CAVE_ECHO -> level.playSound(null, origin.below(4), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1, .7f);
            case WATCHER -> { entity.appearAt(player.position().add(player.getLookAngle().multiply(9, 0, 9)).add(0, 2, 0)); entity.holdFor(80); }
            case SHUDDER -> { entity.glitchFor(40); level.playSound(null, entity.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, .4f, .5f); }
            case COLD_BREATH -> level.sendParticles(ParticleTypes.CLOUD, eye.x, eye.y, eye.z, 18, .2, .15, .2, .025);
            case DARK_SPIRAL -> {
                for (int i = 0; i < 28; i++) {
                    double angle = i * Math.PI / 6;
                    level.sendParticles(ParticleTypes.SMOKE, entity.getX() + Math.cos(angle), entity.getY() + i / 20d,
                            entity.getZ() + Math.sin(angle), 1, 0, 0, 0, .01);
                }
            }
            case HEARTBEAT -> level.playSound(null, origin, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, .8f, .8f);
            case WARNING_VOICE -> runtime.playVoice(entity, 6);
            case WARNING_TITLE -> player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("ER ERINNERT SICH.")));
        }
    }
}
