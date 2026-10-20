package ru.whysafe.chip8.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

import ru.whysafe.chip8.core.Chip8;

/**
 * The COSMAC VIP hex keypad, clickable with the mouse. The physical layout maps onto the
 * left block of a PC keyboard (1234 / QWER / ASDF / ZXCV), matched by key position, so it
 * works the same in any keyboard layout.
 */
final class KeypadPanel extends JComponent {

    /** CHIP-8 key values in the order they sit on the VIP keypad, row by row. */
    static final int[] LAYOUT = {0x1, 0x2, 0x3, 0xC, 0x4, 0x5, 0x6, 0xD, 0x7, 0x8, 0x9, 0xE, 0xA, 0x0, 0xB, 0xF};
    static final int[] KEY_CODES = {
        KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
        KeyEvent.VK_Q, KeyEvent.VK_W, KeyEvent.VK_E, KeyEvent.VK_R,
        KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_F,
        KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V};
    private static final String[] LABELS = {"1", "2", "3", "4", "Q", "W", "E", "R", "A", "S", "D", "F", "Z", "X", "C", "V"};

    private final Chip8 chip;
    private Color accent;
    private int mouseKey = -1;

    KeypadPanel(Chip8 chip, Color accent) {
        this.chip = chip;
        this.accent = accent;
        setPreferredSize(new Dimension(320, 150));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int slot = slotAt(e.getX(), e.getY());
                if (slot >= 0) {
                    mouseKey = LAYOUT[slot];
                    chip.pressKey(mouseKey);
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (mouseKey >= 0) {
                    chip.releaseKey(mouseKey);
                    mouseKey = -1;
                    repaint();
                }
            }
        });
    }

    /** Maps a PC key code to a CHIP-8 key, or -1. */
    static int chipKeyFor(int keyCode) {
        for (int i = 0; i < KEY_CODES.length; i++) {
            if (KEY_CODES[i] == keyCode) {
                return LAYOUT[i];
            }
        }
        return -1;
    }

    void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    private int slotAt(int x, int y) {
        int cellW = getWidth() / 4;
        int cellH = getHeight() / 4;
        int col = x / Math.max(1, cellW);
        int row = y / Math.max(1, cellH);
        return col < 4 && row < 4 ? row * 4 + col : -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, getWidth(), getHeight());

        int cellW = getWidth() / 4;
        int cellH = getHeight() / 4;
        for (int slot = 0; slot < 16; slot++) {
            int x = (slot % 4) * cellW + 3;
            int y = (slot / 4) * cellH + 3;
            int w = cellW - 6;
            int h = cellH - 6;
            boolean down = chip.isKeyDown(LAYOUT[slot]);

            g.setColor(down ? accent : Theme.PANEL_RAISED);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setColor(down ? accent : Theme.BORDER);
            g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);

            g.setFont(Theme.MONO_LARGE.deriveFont(java.awt.Font.BOLD));
            FontMetrics big = g.getFontMetrics();
            String hex = Integer.toHexString(LAYOUT[slot]).toUpperCase();
            g.setColor(down ? Theme.BACKGROUND : Theme.TEXT);
            g.drawString(hex, x + 10, y + (h + big.getAscent()) / 2 - 2);

            g.setFont(Theme.MONO_SMALL);
            FontMetrics small = g.getFontMetrics();
            g.setColor(down ? Theme.BACKGROUND : Theme.MUTED);
            g.drawString(LABELS[slot], x + w - small.stringWidth(LABELS[slot]) - 8, y + h - 7);
        }
        g.dispose();
    }
}
