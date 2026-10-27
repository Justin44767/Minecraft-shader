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
    private int lastAttackReaction = -100;
    public VerityEntity(EntityType<? extends VerityEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 12, true);
        setNoGravity(true); setPersistenceRequired(); setCustomName(Component.literal("VERITY"));
    }
    @Override protected PathNavigation createNavigation(Level level) { return new FlyingPathNavigation(this, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(MOOD, 70); builder.define(PHASE, 0);
    }
    public int mood() { return entityData.get(MOOD); }
    public int phase() { return entityData.get(PHASE); }
    @Override public void tick() {
        super.tick(); setNoGravity(true);
        if (level() instanceof ServerLevel serverLevel) {
            var runtime = VerityMod.runtime(serverLevel.getServer());
            if (runtime == null) return;
            var snapshot = runtime.engine().worldView();
            // Reject command-spawned duplicates without creating another authoritative companion.
            if (snapshot.entityId() != null && !snapshot.entityId().equals(getUUID())) { discard(); return; }
            entityData.set(MOOD, snapshot.mood()); entityData.set(PHASE, snapshot.phase().ordinal());
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
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); setNoGravity(true); }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount > 0 && tickCount - lastAttackReaction >= 20 && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            var runtime = VerityMod.runtime(level.getServer());
            if (runtime != null) { runtime.attacked(player); lastAttackReaction = tickCount; }
        }
        return false;
    }
}
