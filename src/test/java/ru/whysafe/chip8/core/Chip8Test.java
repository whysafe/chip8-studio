package ru.whysafe.chip8.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import ru.whysafe.chip8.asm.AsmException;
import ru.whysafe.chip8.asm.Assembler;

class Chip8Test {

    private static Chip8 machine(String source, Quirks quirks) throws AsmException {
        Chip8 chip = new Chip8(new Random(1));
        chip.setQuirks(quirks);
        chip.load(Assembler.assemble(source));
        return chip;
    }

    private static Chip8 run(String source, int steps) throws AsmException {
        return run(source, steps, Quirks.COSMAC_VIP);
    }

    private static Chip8 run(String source, int steps, Quirks quirks) throws AsmException {
        Chip8 chip = machine(source, quirks);
        for (int i = 0; i < steps; i++) {
            chip.step();
        }
        return chip;
    }

    @Test
    void loadsFontAndProgramAndStartsAt0x200() throws AsmException {
        Chip8 chip = machine("CLS", Quirks.COSMAC_VIP);
        assertEquals(0x200, chip.pc());
        assertEquals(0x00, chip.readMemory(0x200));
        assertEquals(0xE0, chip.readMemory(0x201));
        assertEquals(0xF0, chip.readMemory(Chip8.FONT_START));
        assertEquals(0x80, chip.readMemory(Chip8.FONT_START + 0xF * 5 + 4));
    }

    @Test
    void addSetsCarryAndFlagWinsOverResultInVf() throws AsmException {
        Chip8 chip = run("LD V0, 200\nLD V1, 100\nADD V0, V1", 3);
        assertEquals(44, chip.register(0));
        assertEquals(1, chip.register(0xF));

        chip = run("LD VF, 200\nLD V1, 100\nADD VF, V1", 3);
        assertEquals(1, chip.register(0xF));
    }

    @Test
    void addImmediateWrapsWithoutTouchingVf() throws AsmException {
        Chip8 chip = run("LD VF, 7\nLD V2, 250\nADD V2, 10", 3);
        assertEquals(4, chip.register(2));
        assertEquals(7, chip.register(0xF));
    }

    @Test
    void subtractionFlagsMeanNoBorrow() throws AsmException {
        Chip8 chip = run("LD V0, 5\nLD V1, 7\nSUB V0, V1", 3);
        assertEquals(254, chip.register(0));
        assertEquals(0, chip.register(0xF));

        chip = run("LD V0, 5\nLD V1, 7\nSUBN V0, V1", 3);
        assertEquals(2, chip.register(0));
        assertEquals(1, chip.register(0xF));

        chip = run("LD V0, 9\nLD V1, 9\nSUB V0, V1", 3);
        assertEquals(0, chip.register(0));
        assertEquals(1, chip.register(0xF));
    }

    @Test
    void shiftQuirkChoosesSourceRegister() throws AsmException {
        String program = "LD V0, 1\nLD V1, 0x81\nSHR V0, V1";
        Chip8 vip = run(program, 3, Quirks.COSMAC_VIP);
        assertEquals(0x40, vip.register(0));
        assertEquals(1, vip.register(0xF));

        Chip8 modern = run(program, 3, Quirks.MODERN);
        assertEquals(0, modern.register(0));
        assertEquals(1, modern.register(0xF));

        Chip8 left = run("LD V0, 0xC1\nSHL V0", 2, Quirks.MODERN);
        assertEquals(0x82, left.register(0));
        assertEquals(1, left.register(0xF));
    }

    @Test
    void logicOpsResetVfOnlyOnVip() throws AsmException {
        String program = "LD VF, 1\nLD V0, 0x0F\nLD V1, 0xF0\nOR V0, V1";
        assertEquals(0, run(program, 4, Quirks.COSMAC_VIP).register(0xF));
        Chip8 modern = run(program, 4, Quirks.MODERN);
        assertEquals(1, modern.register(0xF));
        assertEquals(0xFF, modern.register(0));
        assertEquals(0x00, run("LD V0, 0xAA\nLD V1, 0x55\nAND V0, V1", 3).register(0));
        assertEquals(0xFF, run("LD V0, 0xAA\nLD V1, 0x55\nXOR V0, V1", 3).register(0));
    }

    @Test
    void skipsCompareRegistersAndBytes() throws AsmException {
        Chip8 chip = run("LD V0, 3\nSE V0, 3\nLD V1, 1\nLD V2, 2", 3);
        assertEquals(0, chip.register(1));
        assertEquals(2, chip.register(2));

        chip = run("LD V0, 3\nLD V1, 4\nSNE V0, V1\nLD V2, 1\nLD V3, 1", 4);
        assertEquals(0, chip.register(2));
        assertEquals(1, chip.register(3));
    }

    @Test
    void callAndReturnUseTheStack() throws AsmException {
        Chip8 chip = machine("CALL sub\nLD V1, 1\nhalt: JP halt\nsub: LD V0, 9\nRET", Quirks.COSMAC_VIP);
        chip.step();
        assertEquals(1, chip.stackPointer());
        assertArrayEquals(new int[] {0x202}, chip.callStack());
        chip.step();
        chip.step();
        assertEquals(0x202, chip.pc());
        assertEquals(0, chip.stackPointer());
        chip.step();
        assertEquals(1, chip.register(1));
    }

    @Test
    void stackOverflowAndUnderflowAreReported() throws AsmException {
        Chip8 deep = machine("loop: CALL loop", Quirks.COSMAC_VIP);
        for (int i = 0; i < 16; i++) {
            deep.step();
        }
        EmulationException overflow = assertThrows(EmulationException.class, deep::step);
        assertEquals(0x200, overflow.address());

        Chip8 empty = machine("RET", Quirks.COSMAC_VIP);
        assertThrows(EmulationException.class, empty::step);
    }

    @Test
    void unknownOpcodeStopsWithItsAddress() throws AsmException {
        Chip8 chip = machine("CLS\nDW 0x5121", Quirks.COSMAC_VIP);
        chip.step();
        EmulationException error = assertThrows(EmulationException.class, chip::step);
        assertEquals(0x202, error.address());
        assertTrue(error.getMessage().contains("5121"));
    }

    @Test
    void jumpWithOffsetHonoursQuirk() throws AsmException {
        String program = "LD V0, 4\nLD V3, 8\nJP V0, 0x300";
        assertEquals(0x304, run(program, 3, Quirks.COSMAC_VIP).pc());
        assertEquals(0x308, run(program, 3, Quirks.MODERN).pc());
    }

    @Test
    void bcdWritesHundredsTensOnes() throws AsmException {
        Chip8 chip = run("LD V0, 237\nLD I, 0x300\nLD B, V0", 3);
        assertEquals(2, chip.readMemory(0x300));
        assertEquals(3, chip.readMemory(0x301));
        assertEquals(7, chip.readMemory(0x302));
    }

    @Test
    void storeAndLoadRegistersWithIndexQuirk() throws AsmException {
        String program = "LD V0, 1\nLD V1, 2\nLD V2, 3\nLD I, 0x300\nLD [I], V2\nLD V0, 0\nLD V1, 0\nLD I, 0x300\nLD V1, [I]";
        Chip8 vip = run(program, 9, Quirks.COSMAC_VIP);
        assertEquals(3, vip.readMemory(0x302));
        assertEquals(1, vip.register(0));
        assertEquals(2, vip.register(1));
        assertEquals(0x302, vip.indexRegister());

        Chip8 modern = run(program, 9, Quirks.MODERN);
        assertEquals(0x300, modern.indexRegister());
    }

    @Test
    void addToIndexAndFontLookup() throws AsmException {
        Chip8 chip = run("LD I, 0x100\nLD V0, 0x20\nADD I, V0", 3);
        assertEquals(0x120, chip.indexRegister());

        chip = run("LD V0, 0x1A\nLD F, V0", 2);
        assertEquals(Chip8.FONT_START + 0xA * 5, chip.indexRegister());
    }

    @Test
    void drawingXorsAndReportsCollision() throws AsmException {
        String program = "LD I, dot\nLD V0, 3\nLD V1, 2\nDRW V0, V1, 1\nDRW V0, V1, 1\ndot: DB 0x80";
        Chip8 chip = machine(program, Quirks.COSMAC_VIP);
        for (int i = 0; i < 4; i++) {
            chip.step();
        }
        assertTrue(chip.display().isOn(3, 2));
        assertEquals(0, chip.register(0xF));
        chip.step();
        assertFalse(chip.display().isOn(3, 2));
        assertEquals(1, chip.register(0xF));
    }

    @Test
    void spritesClipOrWrapAtTheEdge() throws AsmException {
        String program = "LD I, bar\nLD V0, 60\nLD V1, 31\nDRW V0, V1, 2\nbar: DB 0xFF, 0xFF";
        Quirks wrapping = new Quirks("wrap", false, false, false, false, false);

        Chip8 clipped = run(program, 4, Quirks.COSMAC_VIP);
        assertEquals(4, clipped.display().litCount());

        Chip8 wrapped = run(program, 4, wrapping);
        assertEquals(16, wrapped.display().litCount());
        assertTrue(wrapped.display().isOn(0, 0));
    }

    @Test
    void spriteOriginWrapsEvenWhenClipping() throws AsmException {
        Chip8 chip = run("LD I, dot\nLD V0, 66\nLD V1, 33\nDRW V0, V1, 1\ndot: DB 0x80", 4);
        assertTrue(chip.display().isOn(2, 1));
    }

    @Test
    void waitForKeyCompletesOnRelease() throws AsmException {
        Chip8 chip = run("LD V3, K\nLD V4, 1", 1);
        assertTrue(chip.isWaitingForKey());
        chip.step();
        assertEquals(0x202, chip.pc());

        chip.pressKey(0xB);
        assertTrue(chip.isWaitingForKey());
        chip.releaseKey(0xB);
        assertFalse(chip.isWaitingForKey());
        assertEquals(0xB, chip.register(3));
        chip.step();
        assertEquals(1, chip.register(4));
    }

    @Test
    void keySkipsReadKeypadState() throws AsmException {
        String program = "LD V0, 5\nSKP V0\nLD V1, 1\nLD V2, 1";
        Chip8 up = run(program, 3);
        assertEquals(1, up.register(1));

        Chip8 down = machine(program, Quirks.COSMAC_VIP);
        down.pressKey(5);
        for (int i = 0; i < 3; i++) {
            down.step();
        }
        assertEquals(0, down.register(1));
        assertEquals(1, down.register(2));
    }

    @Test
    void timersCountDownToZero() throws AsmException {
        Chip8 chip = run("LD V0, 2\nLD DT, V0\nLD ST, V0", 3);
        chip.tickTimers();
        assertEquals(1, chip.delayTimer());
        chip.tickTimers();
        chip.tickTimers();
        assertEquals(0, chip.delayTimer());
        assertEquals(0, chip.soundTimer());
    }

    @Test
    void randomIsMaskedAndDeterministicForASeed() throws AsmException {
        Chip8 a = run("RND V0, 0x0F\nRND V1, 0x0F", 2);
        Chip8 b = run("RND V0, 0x0F\nRND V1, 0x0F", 2);
        assertTrue(a.register(0) <= 0x0F);
        assertEquals(a.register(0), b.register(0));
        assertEquals(a.register(1), b.register(1));
    }

    @Test
    void resetRestoresTheProgramAndClearsState() throws AsmException {
        Chip8 chip = run("LD V0, 1\nLD I, 0x200\nLD [I], V0", 3);
        assertEquals(1, chip.readMemory(0x200));
        chip.reset();
        assertEquals(0x60, chip.readMemory(0x200));
        assertEquals(0, chip.register(0));
        assertEquals(0x200, chip.pc());
        assertEquals(0, chip.cycles());
    }

    @Test
    void rejectsProgramsLargerThanMemory() {
        assertThrows(IllegalArgumentException.class, () -> new Chip8().load(new byte[Chip8.MAX_PROGRAM_SIZE + 1]));
    }
}
