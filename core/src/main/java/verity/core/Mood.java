package verity.core;

public enum Mood {
    ANGRY(0xCC3344), SUSPICIOUS(0xF09538), NEUTRAL(0xF2D35B), HAPPY(0xA6DD65), VERY_HAPPY(0x4DD78A);
    public final int color;
    Mood(int color) { this.color = color; }
    public static int clamp(int value) { return Math.clamp(value, 0, 100); }
    public static Mood of(int value) {
        int clamped = clamp(value);
        return values()[Math.min(4, clamped / 20)];
    }
}
