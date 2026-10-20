package ru.whysafe.chip8.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

import javax.swing.Icon;

/** Toolbar icons drawn with Java2D, so the jar needs no image assets and they stay sharp at any scale. */
final class Icons {

    private static final int SIZE = 16;

    private Icons() {
    }

    private interface Painter {
        void paint(Graphics2D g);
    }

    private static Icon icon(Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c.isEnabled() ? Theme.TEXT : Theme.MUTED);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                painter.paint(g);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }

    static Icon play(Color color) {
        return icon(g -> {
            g.setColor(color);
            g.fillPolygon(new Polygon(new int[] {4, 13, 4}, new int[] {2, 8, 14}, 3));
        });
    }

    static Icon pause() {
        return icon(g -> {
            g.fillRoundRect(3, 2, 4, 12, 2, 2);
            g.fillRoundRect(9, 2, 4, 12, 2, 2);
        });
    }

    static Icon step() {
        return icon(g -> {
            g.fillPolygon(new Polygon(new int[] {2, 10, 2}, new int[] {2, 8, 14}, 3));
            g.fillRoundRect(11, 2, 3, 12, 2, 2);
        });
    }

    static Icon reset() {
        return icon(g -> {
            g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 70, 280, Arc2D.OPEN));
            g.fillPolygon(new Polygon(new int[] {8, 13, 8}, new int[] {0, 3, 6}, 3));
        });
    }

    static Icon open() {
        return icon(g -> {
            g.drawLine(2, 4, 6, 4);
            g.drawLine(6, 4, 8, 6);
            g.drawRoundRect(2, 6, 12, 8, 2, 2);
        });
    }

    static Icon build(Color color) {
        return icon(g -> {
            g.setColor(color);
            g.drawLine(5, 4, 1, 8);
            g.drawLine(1, 8, 5, 12);
            g.drawLine(11, 4, 15, 8);
            g.drawLine(15, 8, 11, 12);
            g.drawLine(9, 3, 7, 13);
        });
    }

    static Icon save() {
        return icon(g -> {
            g.drawRoundRect(2, 2, 12, 12, 2, 2);
            g.fillRect(5, 2, 6, 4);
            g.drawRect(5, 9, 6, 5);
        });
    }
}
