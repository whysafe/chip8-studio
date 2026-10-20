package ru.whysafe.chip8.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import ru.whysafe.chip8.core.Display;

/**
 * Scales the 64x32 frame buffer to the panel. CHIP-8 games erase and redraw sprites every
 * frame, which flickers badly on a modern screen; phosphor persistence lets a switched-off
 * pixel fade over a few frames instead of vanishing, the way a CRT would have shown it.
 */
final class ScreenPanel extends JComponent {

    private static final int LEVELS = 16;
    private static final float FADE = 0.55f;

    private final Display display;
    private final float[] glow = new float[Display.WIDTH * Display.HEIGHT];
    private final Color[] shades = new Color[LEVELS];
    private Theme.Palette palette;
    private boolean persistence = true;
    private boolean pixelGrid = true;

    ScreenPanel(Display display, Theme.Palette palette) {
        this.display = display;
        setPalette(palette);
        setOpaque(true);
        setPreferredSize(new Dimension(704, 352));
        setMinimumSize(new Dimension(320, 160));
    }

    void setPalette(Theme.Palette palette) {
        this.palette = palette;
        for (int i = 0; i < LEVELS; i++) {
            shades[i] = Theme.mix(palette.off, palette.on, (float) i / (LEVELS - 1));
        }
        repaint();
    }

    void setPersistence(boolean persistence) {
        this.persistence = persistence;
    }

    void setPixelGrid(boolean pixelGrid) {
        this.pixelGrid = pixelGrid;
        repaint();
    }

    /** Advances the fade by one frame. */
    void advance() {
        for (int y = 0; y < Display.HEIGHT; y++) {
            for (int x = 0; x < Display.WIDTH; x++) {
                int i = y * Display.WIDTH + x;
                glow[i] = display.isOn(x, y) ? 1f : persistence ? glow[i] * FADE : 0f;
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());

        int bezel = 14;
        int scale = Math.max(1, Math.min((getWidth() - bezel * 2) / Display.WIDTH, (getHeight() - bezel * 2) / Display.HEIGHT));
        int width = scale * Display.WIDTH;
        int height = scale * Display.HEIGHT;
        int left = (getWidth() - width) / 2;
        int top = (getHeight() - height) / 2;

        g.setColor(Theme.PANEL_RAISED);
        g.fillRoundRect(left - bezel, top - bezel, width + bezel * 2, height + bezel * 2, 18, 18);
        g.setColor(Theme.withAlpha(palette.accent, 40));
        g.drawRoundRect(left - bezel, top - bezel, width + bezel * 2 - 1, height + bezel * 2 - 1, 18, 18);
        g.setColor(palette.off);
        g.fillRect(left, top, width, height);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int gap = pixelGrid && scale >= 6 ? 1 : 0;
        for (int y = 0; y < Display.HEIGHT; y++) {
            for (int x = 0; x < Display.WIDTH; x++) {
                int level = Math.round(glow[y * Display.WIDTH + x] * (LEVELS - 1));
                if (level == 0) {
                    continue;
                }
                g.setColor(shades[level]);
                g.fillRect(left + x * scale, top + y * scale, scale - gap, scale - gap);
            }
        }
        g.dispose();
    }
}
