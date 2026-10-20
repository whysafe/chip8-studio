package ru.whysafe.chip8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import ru.whysafe.chip8.asm.AsmException;
import ru.whysafe.chip8.asm.Assembler;
import ru.whysafe.chip8.core.Chip8;
import ru.whysafe.chip8.core.Quirks;

class DemosTest {

    private static Chip8 boot(String key, Quirks quirks) throws AsmException {
        Chip8 chip = new Chip8(new Random(3));
        chip.setQuirks(quirks);
        chip.load(Assembler.assemble(Demos.find(key).source()));
        return chip;
    }

    /** Runs like the window does: 12 instructions per 60 Hz timer tick. */
    private static void runFrames(Chip8 chip, int frames) {
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < 12 && !chip.isWaitingForKey(); i++) {
                chip.step();
            }
            chip.tickTimers();
        }
    }

    @Test
    void everyDemoAssemblesAndRunsUnderBothQuirkSets() throws AsmException {
        for (Demos.Demo demo : Demos.all()) {
            for (Quirks quirks : Quirks.presets()) {
                Chip8 chip = boot(demo.key, quirks);
                // Animations erase and redraw sprites, so any single frame may be caught blank.
                int mostLit = 0;
                for (int f = 0; f < 600; f++) {
                    runFrames(chip, 1);
                    mostLit = Math.max(mostLit, chip.display().litCount());
                }
                assertTrue(mostLit > 0, demo.key + " ничего не нарисовал");
            }
        }
    }

    @Test
    void mazeFillsTheScreenAndWaitsForAKey() throws AsmException {
        Chip8 chip = boot("maze", Quirks.COSMAC_VIP);
        runFrames(chip, 120);
        assertTrue(chip.isWaitingForKey());
        assertEquals(16 * 8 * 4, chip.display().litCount());
    }

    @Test
    void keysDemoShowsThePressedDigit() throws AsmException {
        Chip8 chip = boot("keys", Quirks.COSMAC_VIP);
        runFrames(chip, 2);
        int zero = chip.display().litCount();
        chip.pressKey(0x1);
        chip.releaseKey(0x1);
        runFrames(chip, 2);
        assertTrue(chip.isWaitingForKey());
        assertTrue(chip.soundTimer() > 0);
        assertFalse(zero == chip.display().litCount(), "цифра на экране не сменилась");
        assertEquals(1, chip.register(5));
    }

    @Test
    void squashPaddleFollowsTheKeys() throws AsmException {
        Chip8 chip = boot("squash", Quirks.COSMAC_VIP);
        runFrames(chip, 2);
        int start = chip.register(0xB);
        chip.pressKey(7);
        runFrames(chip, 30);
        chip.releaseKey(7);
        assertTrue(chip.register(0xB) < start, "платформа не сдвинулась влево");
        assertTrue(chip.display().isOn(chip.register(0xB), 30));
    }

    @Test
    void squashEndsWhenTheBallIsMissed() throws AsmException {
        Chip8 chip = boot("squash", Quirks.COSMAC_VIP);
        boolean over = false;
        for (int f = 0; f < 6000 && !over; f++) {
            chip.pressKey(9);
            runFrames(chip, 1);
            over = chip.isWaitingForKey();
        }
        assertTrue(over, "игра должна закончиться, если всё время уводить платформу вправо");
    }
}
