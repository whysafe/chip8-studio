package ru.whysafe.chip8.core;

import java.util.Arrays;
import java.util.Random;

/**
 * CHIP-8 virtual machine: 4 KB of memory, sixteen 8-bit registers, a 16-level call stack,
 * two 60 Hz timers, a 64x32 display and a 16-key hex keypad.
 * <p>
 * The class is not thread-safe; the UI drives it from the Swing event thread only.
 */
public final class Chip8 {

    public static final int MEMORY_SIZE = 4096;
    public static final int PROGRAM_START = 0x200;
    public static final int MAX_PROGRAM_SIZE = MEMORY_SIZE - PROGRAM_START;
    public static final int FONT_START = 0x050;
    public static final int FONT_GLYPH_SIZE = 5;
    private static final int STACK_DEPTH = 16;
    private static final int NO_KEY_WAIT = -1;

    private static final int[] FONT = {
        0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
        0x20, 0x60, 0x20, 0x20, 0x70, // 1
        0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
        0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
        0x90, 0x90, 0xF0, 0x10, 0x10, // 4
        0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
        0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
        0xF0, 0x10, 0x20, 0x40, 0x40, // 7
        0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
        0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
        0xF0, 0x90, 0xF0, 0x90, 0x90, // A
        0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
        0xF0, 0x80, 0x80, 0x80, 0xF0, // C
        0xE0, 0x90, 0x90, 0x90, 0xE0, // D
        0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
        0xF0, 0x80, 0xF0, 0x80, 0x80, // F
    };

    private final byte[] memory = new byte[MEMORY_SIZE];
    private final int[] v = new int[16];
    private final int[] stack = new int[STACK_DEPTH];
    private final boolean[] keys = new boolean[16];
    private final Display display = new Display();
    private final Random random;

    private Quirks quirks = Quirks.COSMAC_VIP;
    private byte[] program = new byte[0];
    private int pc;
    private int index;
    private int sp;
    private int delayTimer;
    private int soundTimer;
    private int keyWaitRegister = NO_KEY_WAIT;
    private long cycles;

    public Chip8() {
        this(new Random());
    }

    public Chip8(Random random) {
        this.random = random;
        reset();
    }

    /** Replaces the program and restarts the machine. */
    public void load(byte[] rom) {
        if (rom.length > MAX_PROGRAM_SIZE) {
            throw new IllegalArgumentException(
                "Программа занимает " + rom.length + " байт, а в память помещается " + MAX_PROGRAM_SIZE);
        }
        program = rom.clone();
        reset();
    }

    /** Restarts the current program from a clean state, like the reset switch on the VIP. */
    public void reset() {
        Arrays.fill(memory, (byte) 0);
        for (int i = 0; i < FONT.length; i++) {
            memory[FONT_START + i] = (byte) FONT[i];
        }
        System.arraycopy(program, 0, memory, PROGRAM_START, program.length);
        Arrays.fill(v, 0);
        Arrays.fill(stack, 0);
        Arrays.fill(keys, false);
        display.clear();
        pc = PROGRAM_START;
        index = 0;
        sp = 0;
        delayTimer = 0;
        soundTimer = 0;
        keyWaitRegister = NO_KEY_WAIT;
        cycles = 0;
    }

    /** Executes one instruction. Does nothing while the program waits for a key (FX0A). */
    public void step() {
        if (isWaitingForKey()) {
            return;
        }
        if (pc > MEMORY_SIZE - 2) {
            throw new EmulationException(pc, "счётчик команд вышел за пределы памяти");
        }
        int opcode = (read(pc) << 8) | read(pc + 1);
        int address = pc;
        pc += 2;
        cycles++;
        execute(address, opcode);
    }

    /** Called 60 times per second. */
    public void tickTimers() {
        if (delayTimer > 0) {
            delayTimer--;
        }
        if (soundTimer > 0) {
            soundTimer--;
        }
    }

    public void pressKey(int key) {
        keys[checkKey(key)] = true;
    }

    /** FX0A on the COSMAC VIP completes on key release, not press, and so does this. */
    public void releaseKey(int key) {
        keys[checkKey(key)] = false;
        if (isWaitingForKey()) {
            v[keyWaitRegister] = key;
            keyWaitRegister = NO_KEY_WAIT;
        }
    }

    private void execute(int address, int op) {
        int x = (op >>> 8) & 0xF;
        int y = (op >>> 4) & 0xF;
        int n = op & 0xF;
        int nn = op & 0xFF;
        int nnn = op & 0xFFF;

        switch (op >>> 12) {
            case 0x0:
                if (op == 0x00E0) {
                    display.clear();
                } else if (op == 0x00EE) {
                    returnFromSubroutine(address);
                }
                // Any other 0NNN called native RCA 1802 code; there is nothing sensible to run.
                break;
            case 0x1:
                pc = nnn;
                break;
            case 0x2:
                callSubroutine(address, nnn);
                break;
            case 0x3:
                skipIf(v[x] == nn);
                break;
            case 0x4:
                skipIf(v[x] != nn);
                break;
            case 0x5:
                requireLowNibble(address, op, 0);
                skipIf(v[x] == v[y]);
                break;
            case 0x6:
                v[x] = nn;
                break;
            case 0x7:
                v[x] = (v[x] + nn) & 0xFF;
                break;
            case 0x8:
                executeArithmetic(address, op, x, y, n);
                break;
            case 0x9:
                requireLowNibble(address, op, 0);
                skipIf(v[x] != v[y]);
                break;
            case 0xA:
                index = nnn;
                break;
            case 0xB:
                pc = (nnn + (quirks.jumpUsesVx ? v[x] : v[0])) & 0xFFF;
                break;
            case 0xC:
                v[x] = random.nextInt(256) & nn;
                break;
            case 0xD:
                drawSprite(v[x], v[y], n);
                break;
            case 0xE:
                if (nn == 0x9E) {
                    skipIf(keys[v[x] & 0xF]);
                } else if (nn == 0xA1) {
                    skipIf(!keys[v[x] & 0xF]);
                } else {
                    throw unknown(address, op);
                }
                break;
            default:
                executeMisc(address, op, x, nn);
        }
    }

    private void executeArithmetic(int address, int op, int x, int y, int kind) {
        int flag;
        switch (kind) {
            case 0x0:
                v[x] = v[y];
                return;
            case 0x1:
                v[x] |= v[y];
                resetFlagIfQuirk();
                return;
            case 0x2:
                v[x] &= v[y];
                resetFlagIfQuirk();
                return;
            case 0x3:
                v[x] ^= v[y];
                resetFlagIfQuirk();
                return;
            case 0x4: {
                int sum = v[x] + v[y];
                v[x] = sum & 0xFF;
                flag = sum > 0xFF ? 1 : 0;
                break;
            }
            case 0x5:
                flag = v[x] >= v[y] ? 1 : 0;
                v[x] = (v[x] - v[y]) & 0xFF;
                break;
            case 0x6: {
                int source = quirks.shiftUsesVy ? v[y] : v[x];
                flag = source & 1;
                v[x] = source >>> 1;
                break;
            }
            case 0x7:
                flag = v[y] >= v[x] ? 1 : 0;
                v[x] = (v[y] - v[x]) & 0xFF;
                break;
            case 0xE: {
                int source = quirks.shiftUsesVy ? v[y] : v[x];
                flag = (source >>> 7) & 1;
                v[x] = (source << 1) & 0xFF;
                break;
            }
            default:
                throw unknown(address, op);
        }
        // The flag is written last, so "8FY4" ends with VF holding the carry, not the sum.
        v[0xF] = flag;
    }

    private void executeMisc(int address, int op, int x, int kind) {
        switch (kind) {
            case 0x07:
                v[x] = delayTimer;
                break;
            case 0x0A:
                keyWaitRegister = x;
                break;
            case 0x15:
                delayTimer = v[x];
                break;
            case 0x18:
                soundTimer = v[x];
                break;
            case 0x1E:
                index = (index + v[x]) & 0xFFF;
                break;
            case 0x29:
                index = FONT_START + (v[x] & 0xF) * FONT_GLYPH_SIZE;
                break;
            case 0x33:
                write(index, v[x] / 100);
                write(index + 1, v[x] / 10 % 10);
                write(index + 2, v[x] % 10);
                break;
            case 0x55:
                for (int r = 0; r <= x; r++) {
                    write(index + r, v[r]);
                }
                advanceIndexIfQuirk(x);
                break;
            case 0x65:
                for (int r = 0; r <= x; r++) {
                    v[r] = read(index + r);
                }
                advanceIndexIfQuirk(x);
                break;
            default:
                throw unknown(address, op);
        }
    }

    private void drawSprite(int originX, int originY, int height) {
        int startX = originX % Display.WIDTH;
        int startY = originY % Display.HEIGHT;
        boolean collision = false;

        for (int row = 0; row < height; row++) {
            int y = startY + row;
            if (y >= Display.HEIGHT) {
                if (quirks.clipSprites) {
                    break;
                }
                y %= Display.HEIGHT;
            }
            int bits = read(index + row);
            for (int col = 0; col < 8; col++) {
                if ((bits & (0x80 >>> col)) == 0) {
                    continue;
                }
                int x = startX + col;
                if (x >= Display.WIDTH) {
                    if (quirks.clipSprites) {
                        continue;
                    }
                    x %= Display.WIDTH;
                }
                collision |= display.toggle(x, y);
            }
        }
        v[0xF] = collision ? 1 : 0;
    }

    private void callSubroutine(int address, int target) {
        if (sp == STACK_DEPTH) {
            throw new EmulationException(address, "переполнение стека: больше " + STACK_DEPTH + " вложенных CALL");
        }
        stack[sp++] = pc;
        pc = target;
    }

    private void returnFromSubroutine(int address) {
        if (sp == 0) {
            throw new EmulationException(address, "RET без CALL: стек пуст");
        }
        pc = stack[--sp];
    }

    private void skipIf(boolean condition) {
        if (condition) {
            pc += 2;
        }
    }

    private void resetFlagIfQuirk() {
        if (quirks.vfReset) {
            v[0xF] = 0;
        }
    }

    private void advanceIndexIfQuirk(int lastRegister) {
        if (quirks.memoryIncrementsI) {
            index = (index + lastRegister + 1) & 0xFFF;
        }
    }

    private void requireLowNibble(int address, int op, int expected) {
        if ((op & 0xF) != expected) {
            throw unknown(address, op);
        }
    }

    private static EmulationException unknown(int address, int op) {
        return new EmulationException(address, String.format("неизвестная инструкция %04X", op));
    }

    private static int checkKey(int key) {
        if (key < 0 || key > 0xF) {
            throw new IllegalArgumentException("Клавиши CHIP-8 нумеруются от 0 до F, получено " + key);
        }
        return key;
    }

    private int read(int address) {
        return memory[address & 0xFFF] & 0xFF;
    }

    private void write(int address, int value) {
        memory[address & 0xFFF] = (byte) value;
    }

    public int readMemory(int address) {
        return read(address);
    }

    public int register(int r) {
        return v[r];
    }

    /** Test and debugger hook: lets a caller poke a register directly. */
    public void setRegister(int r, int value) {
        v[r] = value & 0xFF;
    }

    public int[] callStack() {
        return Arrays.copyOf(stack, sp);
    }

    public int pc() {
        return pc;
    }

    public int indexRegister() {
        return index;
    }

    public int stackPointer() {
        return sp;
    }

    public int delayTimer() {
        return delayTimer;
    }

    public int soundTimer() {
        return soundTimer;
    }

    public long cycles() {
        return cycles;
    }

    public boolean isWaitingForKey() {
        return keyWaitRegister != NO_KEY_WAIT;
    }

    public boolean isKeyDown(int key) {
        return keys[checkKey(key)];
    }

    public Display display() {
        return display;
    }

    public byte[] program() {
        return program.clone();
    }

    public Quirks quirks() {
        return quirks;
    }

    public void setQuirks(Quirks quirks) {
        this.quirks = quirks;
    }
}
