package ru.whysafe.chip8.core;

/** Monochrome 64x32 frame buffer. Pixels are XOR-drawn, as on the real machine. */
public final class Display {

    public static final int WIDTH = 64;
    public static final int HEIGHT = 32;

    private final boolean[] pixels = new boolean[WIDTH * HEIGHT];

    public void clear() {
        java.util.Arrays.fill(pixels, false);
    }

    /** Flips a pixel and reports whether it was lit before, i.e. whether a collision happened. */
    boolean toggle(int x, int y) {
        int index = y * WIDTH + x;
        boolean wasOn = pixels[index];
        pixels[index] = !wasOn;
        return wasOn;
    }

    public boolean isOn(int x, int y) {
        return pixels[y * WIDTH + x];
    }

    public int litCount() {
        int count = 0;
        for (boolean pixel : pixels) {
            if (pixel) {
                count++;
            }
        }
        return count;
    }

    /** Text rendering of the screen, handy for the headless runner and for tests. */
    public String toAscii(char on, char off) {
        StringBuilder out = new StringBuilder((WIDTH + 1) * HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                out.append(isOn(x, y) ? on : off);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
