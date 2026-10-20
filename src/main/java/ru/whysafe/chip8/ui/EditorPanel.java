package ru.whysafe.chip8.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

import ru.whysafe.chip8.asm.AsmException;
import ru.whysafe.chip8.asm.Assembler;

/** Source editor: write or tweak a program and run it with Ctrl+Enter, errors point at their line. */
final class EditorPanel extends JPanel {

    interface Host {
        void runAssembled(byte[] rom, String title);
    }

    private final Host host;
    private final JTextArea area = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final JButton runButton = new JButton("Собрать и запустить  Ctrl+Enter");
    private final DefaultHighlighter.DefaultHighlightPainter errorPainter =
        new DefaultHighlighter.DefaultHighlightPainter(Theme.withAlpha(Theme.ERROR, 60));
    private String title = "без названия";
    private Color accent;

    EditorPanel(Host host, Color accent) {
        super(new BorderLayout());
        this.host = host;
        this.accent = accent;
        setBackground(Theme.BACKGROUND);

        area.setFont(Theme.MONO_LARGE);
        area.setTabSize(8);
        area.setBackground(Theme.BACKGROUND);
        area.setForeground(Theme.TEXT);
        area.setCaretColor(accent);
        area.setSelectionColor(new Color(0x2E5B47));
        area.setSelectedTextColor(Color.WHITE);
        area.setMargin(new Insets(10, 12, 10, 12));
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { area.getHighlighter().removeAllHighlights(); }
            @Override public void removeUpdate(DocumentEvent e) { area.getHighlighter().removeAllHighlights(); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        JScrollPane scroll = new JScrollPane(area);
        scroll.setRowHeaderView(new LineNumbers(area));
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.BORDER));
        add(scroll, BorderLayout.CENTER);
        add(buildBar(), BorderLayout.SOUTH);

        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, menuKey), "assemble");
        area.getActionMap().put("assemble", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                assembleAndRun();
            }
        });
    }

    private JPanel buildBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));
        status.setFont(Theme.MONO);
        status.setForeground(Theme.MUTED);
        bar.add(status, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        JButton save = new JButton("Сохранить…", Icons.save());
        save.addActionListener(e -> saveSource());
        runButton.setIcon(Icons.build(accent));
        runButton.addActionListener(e -> assembleAndRun());
        buttons.add(save);
        buttons.add(runButton);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    void setSource(String source, String title) {
        this.title = title;
        area.setText(source);
        area.setCaretPosition(0);
        status.setForeground(Theme.MUTED);
        status.setText(title);
    }

    void setAccent(Color accent) {
        this.accent = accent;
        area.setCaretColor(accent);
        runButton.setIcon(Icons.build(accent));
    }

    String source() {
        return area.getText();
    }

    /** Returns false when the source has errors; the error is shown and its line highlighted. */
    boolean assembleAndRun() {
        area.getHighlighter().removeAllHighlights();
        try {
            byte[] rom = Assembler.assemble(area.getText());
            status.setForeground(accent);
            status.setText(String.format("Собрано: %d байт · %s", rom.length, title));
            host.runAssembled(rom, title);
            return true;
        } catch (AsmException e) {
            status.setForeground(Theme.ERROR);
            status.setText(e.getMessage());
            highlightLine(e.line());
            return false;
        }
    }

    private void highlightLine(int line) {
        try {
            int start = area.getLineStartOffset(line - 1);
            int end = area.getLineEndOffset(line - 1);
            area.getHighlighter().addHighlight(start, end, errorPainter);
            area.setCaretPosition(start);
            area.requestFocusInWindow();
        } catch (BadLocationException e) {
            // The reported line is past the end of the text (the source changed meanwhile); nothing to mark.
        }
    }

    private void saveSource() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Исходник CHIP-8 (*.c8s)", "c8s"));
        chooser.setSelectedFile(new File(title.replaceAll("[^\\p{L}\\p{N}_-]+", "_") + ".c8s"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().contains(".")) {
            file = new File(file.getParentFile(), file.getName() + ".c8s");
        }
        try {
            Files.write(file.toPath(), area.getText().getBytes(StandardCharsets.UTF_8));
            status.setForeground(Theme.MUTED);
            status.setText("Сохранено: " + file.getName());
        } catch (IOException e) {
            status.setForeground(Theme.ERROR);
            status.setText("Не удалось сохранить: " + e.getMessage());
        }
    }

    /** Line-number gutter that scrolls together with the text as the scroll pane's row header. */
    private static final class LineNumbers extends JComponent {
        private final JTextArea area;

        LineNumbers(JTextArea area) {
            this.area = area;
            setFont(Theme.MONO);
            area.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { update(); }
                @Override public void removeUpdate(DocumentEvent e) { update(); }
                @Override public void changedUpdate(DocumentEvent e) { update(); }
            });
        }

        private void update() {
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth("0000") + 18, area.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            g.setColor(Theme.PANEL);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);

            FontMetrics text = area.getFontMetrics(area.getFont());
            FontMetrics own = g.getFontMetrics();
            int lineHeight = text.getHeight();
            int top = area.getInsets().top;
            int first = Math.max(0, (clip.y - top) / lineHeight);
            int last = Math.min(area.getLineCount() - 1, (clip.y + clip.height - top) / lineHeight + 1);
            g.setColor(Theme.MUTED);
            for (int line = first; line <= last; line++) {
                String label = String.valueOf(line + 1);
                int y = top + line * lineHeight + text.getAscent();
                g.drawString(label, getWidth() - own.stringWidth(label) - 9, y);
            }
            g.dispose();
        }
    }
}
