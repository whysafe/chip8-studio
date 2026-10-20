package ru.whysafe.chip8.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Timer;

import ru.whysafe.chip8.core.Chip8;
import ru.whysafe.chip8.core.EmulationException;
import ru.whysafe.chip8.core.Quirks;

/**
 * Runs the machine at a chosen instruction rate against a 60 Hz frame clock and owns
 * the debugger state: running or paused, breakpoints, the last stop reason.
 */
final class EmulatorController {

    static final Integer[] SPEEDS = {200, 500, 700, 1000, 1500, 3000, 10000};
    private static final int FRAME_MS = 1000 / 60;

    private final Chip8 chip = new Chip8();
    private final Beeper beeper;
    private final Timer clock;
    private final Set<Integer> breakpoints = new TreeSet<>();
    private final List<Runnable> frameListeners = new ArrayList<>();
    private final List<Runnable> programListeners = new ArrayList<>();

    private String programName = "";
    private String message = "";
    private int speedHz = 700;
    private double pendingCycles;
    private boolean running;
    private boolean resumingFromBreakpoint;

    EmulatorController(Beeper beeper) {
        this.beeper = beeper;
        this.clock = new Timer(FRAME_MS, e -> frame());
        clock.setCoalesce(true);
    }

    void start() {
        clock.start();
    }

    void stop() {
        clock.stop();
        beeper.setPlaying(false);
    }

    /** One 60 Hz tick. Public to the package so the screenshot renderer can drive frames by hand. */
    void frame() {
        if (running) {
            runFrame();
        }
        beeper.setPlaying(running && chip.soundTimer() > 0);
        fire(frameListeners);
    }

    private void runFrame() {
        pendingCycles += speedHz / 60.0;
        int budget = (int) pendingCycles;
        pendingCycles -= budget;
        try {
            for (int n = 0; n < budget && !chip.isWaitingForKey(); n++) {
                if (!resumingFromBreakpoint && breakpoints.contains(chip.pc())) {
                    pause(String.format("Точка останова 0x%03X", chip.pc()));
                    return;
                }
                resumingFromBreakpoint = false;
                chip.step();
            }
            chip.tickTimers();
        } catch (EmulationException e) {
            pause("Остановлено: " + e.getMessage());
        }
    }

    void load(byte[] rom, String name) {
        chip.load(rom);
        programName = name;
        breakpoints.clear();
        pendingCycles = 0;
        message = "";
        running = true;
        fire(programListeners);
        fire(frameListeners);
    }

    void reset() {
        chip.reset();
        message = "Сброс";
        running = true;
        resumingFromBreakpoint = false;
        fire(frameListeners);
    }

    void toggleRunning() {
        if (running) {
            pause("Пауза");
        } else {
            resume();
        }
    }

    void resume() {
        running = true;
        resumingFromBreakpoint = true;
        message = "";
        fire(frameListeners);
    }

    void pause(String reason) {
        running = false;
        message = reason;
        beeper.setPlaying(false);
        fire(frameListeners);
    }

    void step() {
        if (running) {
            pause("Пауза");
        }
        if (chip.isWaitingForKey()) {
            message = "Программа ждёт нажатия клавиши (FX0A)";
        } else {
            try {
                chip.step();
                message = String.format("Шаг: PC = 0x%03X", chip.pc());
            } catch (EmulationException e) {
                message = "Остановлено: " + e.getMessage();
            }
        }
        fire(frameListeners);
    }

    void toggleBreakpoint(int address) {
        if (!breakpoints.remove(address)) {
            breakpoints.add(address);
        }
        fire(frameListeners);
    }

    boolean hasBreakpoint(int address) {
        return breakpoints.contains(address);
    }

    void setSpeed(int hz) {
        speedHz = hz;
    }

    int speed() {
        return speedHz;
    }

    void setQuirks(Quirks quirks) {
        chip.setQuirks(quirks);
    }

    void setMuted(boolean muted) {
        beeper.setMuted(muted);
    }

    void onFrame(Runnable listener) {
        frameListeners.add(listener);
    }

    void onProgramLoaded(Runnable listener) {
        programListeners.add(listener);
    }

    Chip8 chip() {
        return chip;
    }

    boolean isRunning() {
        return running;
    }

    String programName() {
        return programName;
    }

    String message() {
        return message;
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
