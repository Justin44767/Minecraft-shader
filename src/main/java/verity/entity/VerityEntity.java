package verity.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import verity.VerityMod;

public final class VerityEntity extends Mob {
    private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REVEAL = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPEECH_CLIP = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPEECH_LEFT = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSFORM = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GLITCH = SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private int lastAttackReaction = -100;
    private int hiddenTicks, holdTicks, lastDemonAttack = -100;
    public VerityEntity(EntityType<? extends VerityEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 12, true);
        setNoGravity(true); setPersistenceRequired(); setCustomName(Component.literal("VERITY"));
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(MOOD, 70); builder.define(PHASE, 0); builder.define(REVEAL, 0);
        builder.define(SPEECH_CLIP, 0); builder.define(SPEECH_LEFT, 0);
        builder.define(TRANSFORM, 0); builder.define(GLITCH, 0);
    }
    public int mood() { return entityData.get(MOOD); }
    public int phase() { return entityData.get(PHASE); }
    public int revealTicks() { return entityData.get(REVEAL); }
    public void reveal() { entityData.set(REVEAL, 40); }
    public int speechClip() { return entityData.get(SPEECH_CLIP); }
    public int speechLeft() { return entityData.get(SPEECH_LEFT); }
    public void speak(int clip, int duration) { entityData.set(SPEECH_CLIP, clip); entityData.set(SPEECH_LEFT, duration); }
    public int transformTicks() { return entityData.get(TRANSFORM); }
    public int glitchTicks() { return entityData.get(GLITCH); }
    public void hideFor(int ticks) { hiddenTicks = ticks; setInvisible(true); }
    public void holdFor(int ticks) { holdTicks = ticks; getNavigation().stop(); }
    public void glitchFor(int ticks) { entityData.set(GLITCH, ticks); }
    public boolean appearAt(net.minecraft.world.phys.Vec3 pos) {
        if (!(level() instanceof ServerLevel level) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(pos))
                || !level.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(pos))
                || !level.noCollision(this, getBoundingBox().move(pos.subtract(position())))) return false;
        getNavigation().stop(); setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); snapTo(pos); holdFor(40); return true;
    }
    @Override public void tick() {
        super.tick(); setNoGravity(true);
        if (level() instanceof ServerLevel serverLevel) {
            var runtime = VerityMod.runtime(serverLevel.getServer());
            if (runtime == null) return;
            var snapshot = runtime.engine().worldView();
            if (runtime.engine().encounter().stage() == verity.core.VerityWorldState.EncounterStage.SEALED) { discard(); return; }
            // Reject command-spawned duplicates without creating another authoritative companion.
            if (snapshot.entityId() != null && !snapshot.entityId().equals(getUUID())) { discard(); return; }
            if (phase() != snapshot.phase().ordinal() && snapshot.phase() == verity.core.VerityWorldState.Phase.DEMON)
                entityData.set(TRANSFORM, 60);
            entityData.set(MOOD, snapshot.mood()); entityData.set(PHASE, snapshot.phase().ordinal());
            if (speechLeft() > 0) entityData.set(SPEECH_LEFT, speechLeft() - 1);
            if (glitchTicks() > 0) entityData.set(GLITCH, glitchTicks() - 1);
            if (hiddenTicks > 0 && --hiddenTicks == 0) setInvisible(false);
            if (transformTicks() > 0) {
                entityData.set(TRANSFORM, transformTicks() - 1); getNavigation().stop(); setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                if (tickCount % 4 == 0) serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + .5, getZ(), 8, .4, .7, .4, .03);
                return;
            }
            if (revealTicks() > 0) { entityData.set(REVEAL, revealTicks() - 1); getNavigation().stop(); return; }
            if (phase() == 3) {
                var target = serverLevel.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && distanceToSqr(p) < 32 * 32)
                        .min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if (target == null) { getNavigation().stop(); return; }
                var flashlight = target.getMainHandItem().is(VerityMod.FLASHLIGHT) ? target.getMainHandItem() : target.getOffhandItem();
                boolean light = flashlight.is(VerityMod.FLASHLIGHT) && verity.items.VerityFlashlightItem.enabled(flashlight)
                        && target.hasLineOfSight(this) && target.getLookAngle().dot(position().add(0, .5, 0).subtract(target.getEyePosition()).normalize()) > .88;
                getLookControl().setLookAt(target, 35, 35);
                if (light) {
                    getNavigation().stop(); setDeltaMovement(getDeltaMovement().scale(.3));
                    if (tickCount % 40 == 0) runtime.engine().lightRescue(runtime.identity(target));
                } else {
                    if (tickCount % 10 == 0) getNavigation().moveTo(target.getX(), target.getEyeY(), target.getZ(), 1.5);
                    if (distanceToSqr(target) < 4 && tickCount - lastDemonAttack >= 40 && runtime.demonDamage()) {
                        target.hurtServer(serverLevel, damageSources().mobAttack(this), 4); lastDemonAttack = tickCount;
                    }
                }
                return;
            }
            if (holdTicks > 0) { holdTicks--; getNavigation().stop(); return; }
            var speaker = runtime.currentSpeaker();
            if (speaker != null && speaker.level() == level() && distanceToSqr(speaker) <= 32 * 32)
                getLookControl().setLookAt(speaker, 20, 20);
            if (tickCount % 10 == 0 && snapshot.followTarget() != null) {
                var target = serverLevel.getServer().getPlayerList().getPlayer(snapshot.followTarget());
                if (target != null && target.level() == level() && target.isAlive()) {
                    getLookControl().setLookAt(target, 20, 20);
                    if (distanceToSqr(target) > 9 && distanceToSqr(target) < 4096)
                        getNavigation().moveTo(target.getX(), target.getEyeY() + 0.5, target.getZ(), 1.0);
                    else getNavigation().stop();
                } else getNavigation().stop();
            } else if (snapshot.followTarget() == null) getNavigation().stop();
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("VerityHidden", hiddenTicks); out.putInt("VerityHold", holdTicks);
        out.putInt("VerityTransform", transformTicks()); out.putInt("VerityPhase", phase());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); setNoGravity(true);
        hiddenTicks = Math.clamp(in.getIntOr("VerityHidden", 0), 0, 1200); setInvisible(hiddenTicks > 0);
        holdTicks = Math.clamp(in.getIntOr("VerityHold", 0), 0, 1200);
        entityData.set(TRANSFORM, Math.clamp(in.getIntOr("VerityTransform", 0), 0, 60));
        entityData.set(PHASE, Math.clamp(in.getIntOr("VerityPhase", 0), 0, 3));
    }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount > 0 && tickCount - lastAttackReaction >= 20 && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            var runtime = VerityMod.runtime(level.getServer());
            if (runtime != null) { runtime.attacked(player); lastAttackReaction = tickCount; }
        }
        return false;
    }
}
