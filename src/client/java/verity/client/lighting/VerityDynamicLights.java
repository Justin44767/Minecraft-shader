package verity.client.lighting;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.lambdaurora.lambdynlights.api.*;
import dev.lambdaurora.lambdynlights.api.behavior.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import verity.VerityMod;
import verity.core.FlashlightCone;
import verity.items.VerityFlashlightItem;

/** Optional client lighting integration. Minecraft block light/gameplay values remain untouched. */
public final class VerityDynamicLights implements DynamicLightsInitializer {
    private static final int GRID = 7;
    private static final double RANGE = 12, TAN = Math.tan(Math.toRadians(24));
    private record Beam(FlashlightCone cone, Vec3 direction, Vec3 right, Vec3 up, double[][] depths,
                        DynamicLightBehavior.BoundingBox bounds) { }
    private final Map<UUID, Source> sources = new HashMap<>();
    private ClientLevel level;
    private int ticks;
    @Override public void onInitializeDynamicLights(DynamicLightsContext context) {
        var manager = context.dynamicLightBehaviorManager();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != level) { sources.values().forEach(manager::remove); sources.clear(); level = client.level; }
            if (level == null || ++ticks % 4 != 0) return;
            Set<UUID> active = new HashSet<>();
            for (Player player : level.players()) {
                var stack = player.getMainHandItem().is(VerityMod.FLASHLIGHT) ? player.getMainHandItem() : player.getOffhandItem();
                if (player.isRemoved() || !player.isAlive() || !stack.is(VerityMod.FLASHLIGHT) || !VerityFlashlightItem.enabled(stack)) continue;
                active.add(player.getUUID());
                var source = sources.get(player.getUUID());
                if (source == null) {
                    source = new Source(); source.update(level, player, ticks); sources.put(player.getUUID(), source); manager.add(source);
                } else source.update(level, player, ticks);
            }
            sources.entrySet().removeIf(entry -> {
                if (active.contains(entry.getKey())) return false;
                manager.remove(entry.getValue()); return true;
            });
        });
    }
    private static final class Source implements DynamicLightBehavior {
        private volatile Beam beam;
        private final AtomicBoolean dirty = new AtomicBoolean(true);
        void update(ClientLevel level, Player player, int tick) {
            Vec3 origin = player.getEyePosition(), forward = player.getLookAngle().normalize();
            Beam previous = beam;
            if (previous != null && tick % 20 != 0 && origin.distanceToSqr(new Vec3(previous.cone().x(), previous.cone().y(), previous.cone().z())) < .000225
                    && forward.dot(previous.direction()) > .9998) return;
            Vec3 right = forward.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < .01) right = new Vec3(1, 0, 0);
            right = right.normalize(); Vec3 up = right.cross(forward).normalize();
            double[][] depths = new double[GRID][GRID];
            for (int u = 0; u < GRID; u++) for (int v = 0; v < GRID; v++) {
                double a = (u * 2d / (GRID - 1) - 1) * TAN, b = (v * 2d / (GRID - 1) - 1) * TAN;
                Vec3 direction = forward.add(right.scale(a)).add(up.scale(b)).normalize();
                var hit = level.clip(new ClipContext(origin, origin.add(direction.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                depths[u][v] = hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(origin);
            }
            Vec3 end = origin.add(forward.scale(RANGE)); double spread = RANGE * TAN + 1;
            var bounds = new BoundingBox((int)Math.floor(Math.min(origin.x, end.x) - spread), (int)Math.floor(Math.min(origin.y, end.y) - spread),
                    (int)Math.floor(Math.min(origin.z, end.z) - spread), (int)Math.ceil(Math.max(origin.x, end.x) + spread),
                    (int)Math.ceil(Math.max(origin.y, end.y) + spread), (int)Math.ceil(Math.max(origin.z, end.z) + spread));
            beam = new Beam(new FlashlightCone(origin.x, origin.y, origin.z, forward.x, forward.y, forward.z, RANGE), forward, right, up, depths, bounds);
            dirty.set(true);
        }
        @Override public double lightAtPos(BlockPos pos, double falloffRatio) {
            Beam b = beam; if (b == null) return 0;
            Vec3 relative = new Vec3(pos.getX() + .5 - b.cone().x(), pos.getY() + .5 - b.cone().y(), pos.getZ() + .5 - b.cone().z());
            double depth = relative.dot(b.direction());
            if (depth <= 0) return 0;
            double u = relative.dot(b.right()) / (depth * TAN), v = relative.dot(b.up()) / (depth * TAN);
            if (Math.abs(u) > 1 || Math.abs(v) > 1) return 0;
            int ix = Math.clamp((int)Math.round((u + 1) * .5 * (GRID - 1)), 0, GRID - 1);
            int iy = Math.clamp((int)Math.round((v + 1) * .5 * (GRID - 1)), 0, GRID - 1);
            return b.cone().luminance(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, b.depths()[ix][iy], falloffRatio);
        }
        @Override public BoundingBox getBoundingBox() { return beam.bounds(); }
        @Override public boolean hasChanged() { return dirty.getAndSet(false); }
    }
}
