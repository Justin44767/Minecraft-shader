package verity.core;

/** Original spotlight math, independent of rendering/lighting libraries. */
public record FlashlightCone(double x, double y, double z, double dx, double dy, double dz, double range) {
    public FlashlightCone {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(x + y + z + length + range) || length < .00001 || range <= 0 || range > 32)
            throw new IllegalArgumentException("Invalid flashlight cone");
        dx /= length; dy /= length; dz /= length;
    }
    public double luminance(double px, double py, double pz, double surfaceDistance, double falloffRatio) {
        double vx = px - x, vy = py - y, vz = pz - z;
        double distance = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (distance > range || distance > surfaceDistance + .8) return 0;
        if (distance < .05) return 15;
        double cosine = (vx * dx + vy * dy + vz * dz) / distance;
        double outer = Math.cos(Math.toRadians(24)), inner = Math.cos(Math.toRadians(15));
        double edge = Math.clamp((cosine - outer) / (inner - outer), 0, 1);
        double beam = Math.max(0, 15 - distance * 15 / range * Math.max(.1, falloffRatio));
        return Math.clamp(beam * edge, 0, 15);
    }
}
