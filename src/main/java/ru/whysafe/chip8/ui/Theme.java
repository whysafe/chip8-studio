package ru.whysafe.chip8.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

import javax.swing.UIManager;

/** Colours and fonts of the studio: a dark workbench around a screen whose colours are switchable. */
final class Theme {

    static final Color BACKGROUND = new Color(0x111318);
    static final Color PANEL = new Color(0x191C22);
    static final Color PANEL_RAISED = new Color(0x22262E);
    static final Color BORDER = new Color(0x2C313B);
    static final Color TEXT = new Color(0xD9DCE3);
    static final Color MUTED = new Color(0x7A828E);
    static final Color ERROR = new Color(0xFF6B6B);
    static final Color BREAKPOINT = new Color(0xF0524F);

    static final Font MONO = monoFont(13f);
    static final Font MONO_SMALL = monoFont(11f);
    static final Font MONO_LARGE = monoFont(14f);
    static final Font UI = new Font(Font.DIALOG, Font.PLAIN, 13);
    static final Font UI_BOLD = new Font(Font.DIALOG, Font.BOLD, 12);

    /** Screen colours. The accent tints the debugger so the whole window matches the screen. */
    static final class Palette {
        final String name;
        final Color on;
        final Color off;
        final Color accent;

        Palette(String name, int on, int off, int accent) {
            this.name = name;
            this.on = new Color(on);
            this.off = new Color(off);
            this.accent = new Color(accent);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Palette> PALETTES = Arrays.asList(
        new Palette("Фосфор", 0x3DFF86, 0x06110A, 0x3DFF86),
        new Palette("Янтарь", 0xFFB53D, 0x130B02, 0xFFB53D),
        new Palette("Карманная LCD", 0x173A16, 0x9DBB2C, 0xB5D33A),
        new Palette("Бумага", 0x24262B, 0xEAE4D3, 0xEAD9A6),
        new Palette("Лёд", 0xA3ECFF, 0x05131D, 0x7FD8F5));

    private Theme() {
    }

    static Color mix(Color from, Color to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * k),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * k),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * k));
    }

    static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** Dark Nimbus: one of the few built-in looks that can be recoloured through UIManager keys alone. */
    static void install() {
        UIManager.put("control", PANEL);
        UIManager.put("info", PANEL_RAISED);
        UIManager.put("nimbusBase", new Color(0x1C2027));
        UIManager.put("nimbusBlueGrey", new Color(0x2A2F38));
        UIManager.put("nimbusLightBackground", BACKGROUND);
        UIManager.put("nimbusFocus", new Color(0x4C8C6A));
        UIManager.put("nimbusSelectionBackground", new Color(0x2E5B47));
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusDisabledText", MUTED);
        UIManager.put("text", TEXT);
        UIManager.put("defaultFont", UI);
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
        } catch (Exception e) {
            // Falling back to the default look is harmless: the custom-painted parts keep their colours.
        }
    }

    private static Font monoFont(float size) {
        List<String> installed = Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : new String[] {"JetBrains Mono", "Cascadia Mono", "Consolas", "DejaVu Sans Mono"}) {
            if (installed.contains(family)) {
                return new Font(family, Font.PLAIN, Math.round(size));
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
    }
}
