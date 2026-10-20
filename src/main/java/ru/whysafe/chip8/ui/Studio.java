package ru.whysafe.chip8.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import ru.whysafe.chip8.Demos;
import ru.whysafe.chip8.asm.Disassembler;
import ru.whysafe.chip8.core.Chip8;
import ru.whysafe.chip8.core.Quirks;

/** Main window: screen and editor tabs on the left, debugger column on the right. */
public final class Studio extends JFrame implements EditorPanel.Host {

    private final Beeper beeper = new Beeper();
    private final EmulatorController controller = new EmulatorController(beeper);
    private Theme.Palette palette = Theme.PALETTES.get(0);

    private final ScreenPanel screen = new ScreenPanel(controller.chip().display(), palette);
    private final RegistersPanel registers = new RegistersPanel(controller.chip(), palette.accent);
    private final DisassemblyPanel listing = new DisassemblyPanel(controller, palette.accent);
    private final KeypadPanel keypad = new KeypadPanel(controller.chip(), palette.accent);
    private final EditorPanel editor = new EditorPanel(this, palette.accent);
    private final JTabbedPane tabs = new JTabbedPane();

    private final JButton runButton = new JButton("Пауза", Icons.pause());
    private final JComboBox<Demos.Demo> demoBox = new JComboBox<>(Demos.all().toArray(new Demos.Demo[0]));
    private final JLabel hint = new JLabel(" ");
    private final JLabel statusLeft = new JLabel(" ");
    private final JLabel statusRight = new JLabel(" ");
    private boolean updatingDemoBox;

    private Studio() {
        super("CHIP-8 Studio");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                controller.stop();
                beeper.close();
                System.exit(0);
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);
        root.add(buildToolbar(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildSidebar(), BorderLayout.EAST);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        installShortcuts(root);
        installKeyboard();
        installDropTarget(root);

        controller.onFrame(this::onFrame);
        controller.onProgramLoaded(listing::reload);

        setMinimumSize(new Dimension(980, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
    }

    // ---- layout -------------------------------------------------------------------------

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JPanel left = row();
        JLabel brand = new JLabel("CHIP-8 STUDIO");
        brand.setFont(Theme.MONO_LARGE.deriveFont(java.awt.Font.BOLD));
        brand.setForeground(palette.accent);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        left.add(brand);

        JButton open = new JButton("Открыть", Icons.open());
        open.setToolTipText("Открыть ROM (.ch8) или исходник (.c8s) — Ctrl+O. Файл можно перетащить в окно.");
        open.addActionListener(e -> chooseFile());
        left.add(open);

        demoBox.setToolTipText("Встроенные демо-программы с исходниками");
        demoBox.addActionListener(e -> {
            if (!updatingDemoBox && demoBox.getSelectedItem() != null) {
                loadDemo((Demos.Demo) demoBox.getSelectedItem());
            }
        });
        left.add(label("Демо"));
        left.add(demoBox);
        left.add(Box.createHorizontalStrut(10));

        runButton.setToolTipText("Пуск / пауза — F5");
        runButton.addActionListener(e -> controller.toggleRunning());
        JButton step = new JButton("Шаг", Icons.step());
        step.setToolTipText("Выполнить одну инструкцию — F10");
        step.addActionListener(e -> controller.step());
        JButton reset = new JButton("Сброс", Icons.reset());
        reset.setToolTipText("Перезапустить программу — Ctrl+R");
        reset.addActionListener(e -> controller.reset());
        left.add(runButton);
        left.add(step);
        left.add(reset);
        bar.add(left, BorderLayout.WEST);

        JPanel right = row();
        JComboBox<Integer> speed = new JComboBox<>(EmulatorController.SPEEDS);
        speed.setSelectedItem(controller.speed());
        speed.setToolTipText("Инструкций в секунду. Большинство игр рассчитаны на 500–1000.");
        speed.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, value + " Гц", index, selected, focused);
            }
        });
        speed.addActionListener(e -> controller.setSpeed((Integer) speed.getSelectedItem()));

        JComboBox<Quirks> quirks = new JComboBox<>(Quirks.presets());
        quirks.setToolTipText("Набор особенностей интерпретатора. Если игра ведёт себя странно — переключите.");
        quirks.addActionListener(e -> controller.setQuirks((Quirks) quirks.getSelectedItem()));

        JComboBox<Theme.Palette> palettes = new JComboBox<>(Theme.PALETTES.toArray(new Theme.Palette[0]));
        palettes.addActionListener(e -> applyPalette((Theme.Palette) palettes.getSelectedItem(), brand));

        right.add(label("Скорость"));
        right.add(speed);
        right.add(label("Режим"));
        right.add(quirks);
        right.add(label("Экран"));
        right.add(palettes);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildCenter() {
        JPanel screenTab = new JPanel(new BorderLayout());
        screenTab.setBackground(Theme.BACKGROUND);
        screenTab.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        screenTab.add(screen, BorderLayout.CENTER);

        JPanel under = new JPanel(new BorderLayout());
        under.setOpaque(false);
        under.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        hint.setFont(Theme.MONO);
        hint.setForeground(Theme.MUTED);
        under.add(hint, BorderLayout.WEST);

        JPanel toggles = row();
        JCheckBox glow = checkbox("Послесвечение", true, on -> screen.setPersistence(on));
        glow.setToolTipText("Плавное затухание пикселей убирает мерцание, типичное для CHIP-8");
        toggles.add(glow);
        toggles.add(checkbox("Сетка пикселей", true, on -> screen.setPixelGrid(on)));
        toggles.add(checkbox("Звук", true, on -> controller.setMuted(!on)));
        under.add(toggles, BorderLayout.EAST);
        screenTab.add(under, BorderLayout.SOUTH);

        tabs.addTab("Экран", screenTab);
        tabs.addTab("Ассемблер", editor);
        return tabs;
    }

    private JComponent buildSidebar() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.PANEL);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        side.setPreferredSize(new Dimension(340, 600));
        side.add(section("Регистры", registers), BorderLayout.NORTH);
        side.add(section("Код  ·  клик слева — точка останова", listing), BorderLayout.CENTER);
        side.add(section("Клавиатура", keypad), BorderLayout.SOUTH);
        return side;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        statusLeft.setFont(Theme.MONO);
        statusLeft.setForeground(Theme.MUTED);
        statusRight.setFont(Theme.MONO);
        statusRight.setForeground(Theme.MUTED);
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    private static JPanel section(String title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.PANEL);
        JLabel caption = new JLabel(title.toUpperCase());
        caption.setFont(Theme.UI_BOLD);
        caption.setForeground(Theme.MUTED);
        caption.setBorder(BorderFactory.createEmptyBorder(10, 12, 2, 12));
        panel.add(caption, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        return panel;
    }

    private static JPanel row() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        return label;
    }

    private interface Toggle {
        void set(boolean on);
    }

    private static JCheckBox checkbox(String text, boolean initial, Toggle toggle) {
        JCheckBox box = new JCheckBox(text, initial);
        box.setOpaque(false);
        box.setForeground(Theme.MUTED);
        box.setFocusable(false);
        box.addActionListener(e -> toggle.set(box.isSelected()));
        return box;
    }

    // ---- input --------------------------------------------------------------------------

    private void installShortcuts(JComponent root) {
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "run", controller::toggleRunning);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0), "step", controller::step);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_R, menuKey), "reset", controller::reset);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_O, menuKey), "open", this::chooseFile);
    }

    private static void bind(JComponent root, KeyStroke stroke, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** Hex keypad on the keyboard, everywhere except while typing in the editor. */
    private void installKeyboard() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            Component source = e.getComponent();
            if (source == null || source instanceof JTextComponent || e.isControlDown() || e.isAltDown()) {
                return false;
            }
            Window window = source instanceof Window ? (Window) source : SwingUtilities.getWindowAncestor(source);
            if (window != this) {
                return false;
            }
            int key = KeypadPanel.chipKeyFor(e.getKeyCode());
            if (key < 0) {
                return false;
            }
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                controller.chip().pressKey(key);
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                controller.chip().releaseKey(key);
            }
            keypad.repaint();
            return true;
        });
    }

    private void installDropTarget(JComponent root) {
        root.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        openFile(files.get(0));
                        return true;
                    }
                } catch (Exception e) {
                    showError("Не удалось открыть перетащенный файл: " + e.getMessage());
                }
                return false;
            }
        });
    }

    // ---- actions ------------------------------------------------------------------------

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Программы CHIP-8 (*.ch8, *.c8s)", "ch8", "c8s"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    void openFile(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            selectDemo(null);
            hint.setText(file.getName());
            if (file.getName().toLowerCase().endsWith(".c8s")) {
                editor.setSource(new String(bytes, StandardCharsets.UTF_8), file.getName());
                if (!editor.assembleAndRun()) {
                    tabs.setSelectedComponent(editor);
                }
                return;
            }
            if (bytes.length > Chip8.MAX_PROGRAM_SIZE) {
                showError(String.format("%s: %d байт, а в память CHIP-8 помещается %d.",
                    file.getName(), bytes.length, Chip8.MAX_PROGRAM_SIZE));
                return;
            }
            editor.setSource(Disassembler.toSource(bytes, Chip8.PROGRAM_START, file.getName()), file.getName());
            runAssembled(bytes, file.getName());
        } catch (IOException e) {
            showError("Не удалось прочитать " + file.getName() + ": " + e.getMessage());
        }
    }

    void loadDemo(Demos.Demo demo) {
        selectDemo(demo);
        hint.setText(demo.title + " — " + demo.hint);
        editor.setSource(demo.source(), demo.title);
        editor.assembleAndRun();
    }

    @Override
    public void runAssembled(byte[] rom, String title) {
        controller.load(rom, title);
        setTitle("CHIP-8 Studio — " + title);
        tabs.setSelectedIndex(0);
    }

    private void selectDemo(Demos.Demo demo) {
        updatingDemoBox = true;
        demoBox.setSelectedItem(demo);
        updatingDemoBox = false;
    }

    private void applyPalette(Theme.Palette next, JLabel brand) {
        palette = next;
        screen.setPalette(next);
        registers.setAccent(next.accent);
        listing.setAccent(next.accent);
        keypad.setAccent(next.accent);
        editor.setAccent(next.accent);
        brand.setForeground(next.accent);
        updateRunButton();
    }

    private void onFrame() {
        screen.advance();
        registers.refresh();
        listing.refresh();
        keypad.repaint();
        updateRunButton();

        Chip8 chip = controller.chip();
        String state = controller.isRunning()
            ? chip.isWaitingForKey() ? "ждёт клавишу" : "выполняется"
            : "пауза";
        statusLeft.setText(String.format("%s  ·  %d байт  ·  %s  ·  %s",
            controller.programName(), chip.program().length, chip.quirks().name, state));
        String message = controller.message();
        statusRight.setForeground(message.startsWith("Остановлено") ? Theme.ERROR : Theme.MUTED);
        statusRight.setText(message.isEmpty() ? String.format("циклов: %,d", chip.cycles()) : message);
    }

    private void updateRunButton() {
        boolean running = controller.isRunning();
        runButton.setText(running ? "Пауза" : "Пуск");
        runButton.setIcon(running ? Icons.pause() : Icons.play(palette.accent));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "CHIP-8 Studio", JOptionPane.ERROR_MESSAGE);
    }

    // ---- entry points -------------------------------------------------------------------

    public static void open(File file) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            Studio studio = new Studio();
            studio.setVisible(true);
            if (file != null) {
                studio.openFile(file);
            } else {
                studio.loadDemo(Demos.all().get(0));
            }
            studio.controller.start();
        });
    }

    /** Renders the window with a demo after a number of frames into a PNG, without a running clock. */
    public static void screenshot(Demos.Demo demo, File target, int frames) throws IOException {
        try {
            SwingUtilities.invokeAndWait(() -> {
                Theme.install();
                Studio studio = new Studio();
                studio.getContentPane().setPreferredSize(new Dimension(1280, 800));
                studio.pack();
                studio.setVisible(true);
                studio.loadDemo(demo);
                for (int i = 0; i < frames; i++) {
                    studio.controller.frame();
                }
                studio.controller.pause("Пауза");
                studio.controller.frame();
                studio.validate();
                BufferedImage image = new BufferedImage(studio.getContentPane().getWidth(),
                    studio.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                studio.getContentPane().paint(g);
                g.dispose();
                try {
                    ImageIO.write(image, "png", target);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                studio.beeper.close();
                studio.setVisible(false);
            });
        } catch (InterruptedException | InvocationTargetException e) {
            throw new IOException("Не удалось сделать снимок окна", e);
        }
    }
}
