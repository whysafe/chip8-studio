package ru.whysafe.chip8.core;

/**
 * Behaviour differences between CHIP-8 implementations. Many ROMs were written
 * for one specific interpreter and break subtly on another, so these are switchable.
 */
public final class Quirks {

    /** Original COSMAC VIP interpreter (1977). */
    public static final Quirks COSMAC_VIP = new Quirks("COSMAC VIP", true, true, true, false, true);

    /** CHIP-48 / SUPER-CHIP on HP calculators: the behaviour most 90s-era ROMs expect. */
    public static final Quirks MODERN = new Quirks("CHIP-48 / SUPER-CHIP", false, false, false, true, true);

    private static final Quirks[] PRESETS = {COSMAC_VIP, MODERN};

    public final String name;
    /** 8XY1, 8XY2, 8XY3 set VF to 0. */
    public final boolean vfReset;
    /** FX55 / FX65 leave I pointing past the last register written. */
    public final boolean memoryIncrementsI;
    /** 8XY6 / 8XYE shift VY into VX instead of shifting VX in place. */
    public final boolean shiftUsesVy;
    /** BNNN is treated as BXNN: jump to XNN + VX. */
    public final boolean jumpUsesVx;
    /** Sprites are cut at the screen edge instead of wrapping to the other side. */
    public final boolean clipSprites;

    public Quirks(String name, boolean vfReset, boolean memoryIncrementsI, boolean shiftUsesVy,
                  boolean jumpUsesVx, boolean clipSprites) {
        this.name = name;
        this.vfReset = vfReset;
        this.memoryIncrementsI = memoryIncrementsI;
        this.shiftUsesVy = shiftUsesVy;
        this.jumpUsesVx = jumpUsesVx;
        this.clipSprites = clipSprites;
    }

    public static Quirks[] presets() {
        return PRESETS.clone();
    }

    public static Quirks byKey(String key) {
        switch (key.toLowerCase()) {
            case "vip":
            case "cosmac":
                return COSMAC_VIP;
            case "modern":
            case "schip":
                return MODERN;
            default:
                throw new IllegalArgumentException("Неизвестный набор совместимости: " + key + " (vip | modern)");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
