package ru.whysafe.chip8.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import ru.whysafe.chip8.core.Chip8;

/** Register file view; values that changed since the previous refresh are drawn in the accent colour. */
final class RegistersPanel extends JComponent {

    private static final int ROW = 21;
    private static final int PAD = 12;

    private final Chip8 chip;
    private final int[] previous = new int[16];
    private final boolean[] changed = new boolean[16];
    private Color accent;

    RegistersPanel(Chip8 chip, Color accent) {
        this.chip = chip;
        this.accent = accent;
        setPreferredSize(new Dimension(320, PAD * 2 + ROW * 7));
    }

    void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    void refresh() {
        for (int r = 0; r < 16; r++) {
            int value = chip.register(r);
            changed[r] = value != previous[r];
            previous[r] = value;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(Theme.MONO);
        FontMetrics fm = g.getFontMetrics();
        int column = (getWidth() - PAD * 2) / 4;
        int baseline = PAD + fm.getAscent();

        cell(g, PAD, baseline, "PC", String.format("%03X", chip.pc()), false);
        cell(g, PAD + column, baseline, "I", String.format("%03X", chip.indexRegister()), false);
        cell(g, PAD + column * 2, baseline, "DT", String.format("%02X", chip.delayTimer()), chip.delayTimer() > 0);
        cell(g, PAD + column * 3, baseline, "ST", String.format("%02X", chip.soundTimer()), chip.soundTimer() > 0);

        for (int r = 0; r < 16; r++) {
            int x = PAD + (r % 4) * column;
            int y = baseline + ROW * (1 + r / 4) + 4;
            cell(g, x, y, "V" + Integer.toHexString(r).toUpperCase(), String.format("%02X", chip.register(r)), changed[r]);
        }

        int stackY = baseline + ROW * 5 + 10;
        g.setColor(Theme.MUTED);
        g.drawString("стек", PAD, stackY);
        int[] stack = chip.callStack();
        StringBuilder frames = new StringBuilder();
        for (int i = stack.length - 1; i >= 0 && frames.length() < 28; i--) {
            frames.append(String.format("%03X ", stack[i]));
        }
        g.setColor(stack.length == 0 ? Theme.MUTED : Theme.TEXT);
        g.drawString(stack.length == 0 ? "пуст" : frames.toString().trim(), PAD + fm.stringWidth("стек  "), stackY);

        g.setColor(Theme.BORDER);
        g.drawLine(PAD, baseline + 9, getWidth() - PAD, baseline + 9);
        g.dispose();
    }

    private void cell(Graphics2D g, int x, int y, String name, String value, boolean highlight) {
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString(name, x, y);
        g.setColor(highlight ? accent : Theme.TEXT);
        g.drawString(value, x + fm.stringWidth("VF "), y);
    }
}
