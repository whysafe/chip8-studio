package ru.whysafe.chip8.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import ru.whysafe.chip8.asm.Disassembler;
import ru.whysafe.chip8.core.Chip8;

/** Listing of the loaded program with the current instruction marked; a click in the gutter toggles a breakpoint. */
final class DisassemblyPanel extends JPanel {

    private static final int GUTTER = 22;

    private final EmulatorController controller;
    private final DefaultListModel<Disassembler.Line> model = new DefaultListModel<>();
    private final JList<Disassembler.Line> list = new JList<>(model);
    private final Map<Integer, Integer> rowByAddress = new HashMap<>();
    private Color accent;
    private int lastPc = -1;

    DisassemblyPanel(EmulatorController controller, Color accent) {
        super(new BorderLayout());
        this.controller = controller;
        this.accent = accent;

        list.setBackground(Theme.PANEL);
        list.setFixedCellHeight(19);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new LineRenderer());
        list.setFocusable(false);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = list.locationToIndex(e.getPoint());
                boolean inGutter = e.getX() < GUTTER;
                if (row >= 0 && e.getClickCount() == (inGutter ? 1 : 2)) {
                    controller.toggleBreakpoint(model.get(row).address);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.PANEL);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(320, 260));
    }

    void setAccent(Color accent) {
        this.accent = accent;
        list.repaint();
    }

    void reload() {
        model.clear();
        rowByAddress.clear();
        List<Disassembler.Line> lines = Disassembler.disassemble(controller.chip().program(), Chip8.PROGRAM_START);
        for (Disassembler.Line line : lines) {
            rowByAddress.put(line.address, model.size());
            model.addElement(line);
        }
        lastPc = -1;
        refresh();
    }

    /** Repaints the PC marker; the listing only scrolls after the program when it is paused. */
    void refresh() {
        int pc = controller.chip().pc();
        if (pc != lastPc && !controller.isRunning()) {
            Integer row = rowByAddress.get(pc);
            if (row != null) {
                list.ensureIndexIsVisible(Math.min(model.size() - 1, row + 4));
                list.ensureIndexIsVisible(Math.max(0, row - 4));
            }
        }
        lastPc = pc;
        list.repaint();
    }

    private final class LineRenderer extends JLabel implements ListCellRenderer<Disassembler.Line> {
        private boolean current;
        private boolean breakpoint;

        LineRenderer() {
            setOpaque(true);
            setFont(Theme.MONO);
            setBorder(BorderFactory.createEmptyBorder(0, GUTTER, 0, 8));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Disassembler.Line> owner, Disassembler.Line line,
                                                      int index, boolean selected, boolean focused) {
            current = line.address == controller.chip().pc();
            breakpoint = controller.hasBreakpoint(line.address);
            setText(String.format("%03X   %04X   %s", line.address, line.opcode, line.text));
            setBackground(current ? Theme.withAlpha(accent, 46) : Theme.PANEL);
            setForeground(current ? accent : line.text.startsWith("DW") || line.text.startsWith("DB") ? Theme.MUTED : Theme.TEXT);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int mid = getHeight() / 2;
            if (breakpoint) {
                g.setColor(Theme.BREAKPOINT);
                g.fillOval(7, mid - 4, 9, 9);
            }
            if (current) {
                g.setColor(accent);
                g.fillRect(0, 0, 3, getHeight());
            }
        }
    }
}
