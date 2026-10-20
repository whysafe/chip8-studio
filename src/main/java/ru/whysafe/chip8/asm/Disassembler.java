package ru.whysafe.chip8.asm;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns opcodes back into assembler text. The output uses exactly the syntax {@link Assembler}
 * accepts, so a disassembled ROM reassembles into the same bytes.
 */
public final class Disassembler {

    /** One decoded word of a program. */
    public static final class Line {
        public final int address;
        public final int opcode;
        public final String text;

        Line(int address, int opcode, String text) {
            this.address = address;
            this.opcode = opcode;
            this.text = text;
        }

        @Override
        public String toString() {
            return String.format("%03X  %04X  %s", address, opcode, text);
        }
    }

    private Disassembler() {
    }

    public static List<Line> disassemble(byte[] rom, int origin) {
        List<Line> lines = new ArrayList<>(rom.length / 2 + 1);
        int offset = 0;
        for (; offset + 1 < rom.length; offset += 2) {
            int opcode = ((rom[offset] & 0xFF) << 8) | (rom[offset + 1] & 0xFF);
            lines.add(new Line(origin + offset, opcode, decode(opcode)));
        }
        if (offset < rom.length) {
            int last = rom[offset] & 0xFF;
            lines.add(new Line(origin + offset, last, String.format("DB 0x%02X", last)));
        }
        return lines;
    }

    /** Source text for the whole ROM, ready to be edited and assembled again. */
    public static String toSource(byte[] rom, int origin, String title) {
        StringBuilder out = new StringBuilder();
        out.append("; ").append(title).append('\n');
        out.append("; Дизассемблировано CHIP-8 Studio. Данные показаны как команды или DW.\n\n");
        for (Line line : disassemble(rom, origin)) {
            out.append(String.format("    %-22s ; %03X%n", line.text, line.address));
        }
        return out.toString();
    }

    public static String decode(int op) {
        int x = (op >>> 8) & 0xF;
        int y = (op >>> 4) & 0xF;
        int n = op & 0xF;
        int nn = op & 0xFF;
        int nnn = op & 0xFFF;

        switch (op >>> 12) {
            case 0x0:
                if (op == 0x00E0) {
                    return "CLS";
                }
                if (op == 0x00EE) {
                    return "RET";
                }
                return "SYS " + addr(nnn);
            case 0x1:
                return "JP " + addr(nnn);
            case 0x2:
                return "CALL " + addr(nnn);
            case 0x3:
                return "SE " + reg(x) + ", " + hexByte(nn);
            case 0x4:
                return "SNE " + reg(x) + ", " + hexByte(nn);
            case 0x5:
                return n == 0 ? "SE " + reg(x) + ", " + reg(y) : data(op);
            case 0x6:
                return "LD " + reg(x) + ", " + hexByte(nn);
            case 0x7:
                return "ADD " + reg(x) + ", " + hexByte(nn);
            case 0x8:
                return decodeArithmetic(op, x, y, n);
            case 0x9:
                return n == 0 ? "SNE " + reg(x) + ", " + reg(y) : data(op);
            case 0xA:
                return "LD I, " + addr(nnn);
            case 0xB:
                return "JP V0, " + addr(nnn);
            case 0xC:
                return "RND " + reg(x) + ", " + hexByte(nn);
            case 0xD:
                return "DRW " + reg(x) + ", " + reg(y) + ", " + n;
            case 0xE:
                if (nn == 0x9E) {
                    return "SKP " + reg(x);
                }
                if (nn == 0xA1) {
                    return "SKNP " + reg(x);
                }
                return data(op);
            default:
                return decodeMisc(op, x, nn);
        }
    }

    private static String decodeArithmetic(int op, int x, int y, int kind) {
        String pair = reg(x) + ", " + reg(y);
        switch (kind) {
            case 0x0: return "LD " + pair;
            case 0x1: return "OR " + pair;
            case 0x2: return "AND " + pair;
            case 0x3: return "XOR " + pair;
            case 0x4: return "ADD " + pair;
            case 0x5: return "SUB " + pair;
            case 0x6: return "SHR " + pair;
            case 0x7: return "SUBN " + pair;
            case 0xE: return "SHL " + pair;
            default: return data(op);
        }
    }

    private static String decodeMisc(int op, int x, int kind) {
        String r = reg(x);
        switch (kind) {
            case 0x07: return "LD " + r + ", DT";
            case 0x0A: return "LD " + r + ", K";
            case 0x15: return "LD DT, " + r;
            case 0x18: return "LD ST, " + r;
            case 0x1E: return "ADD I, " + r;
            case 0x29: return "LD F, " + r;
            case 0x33: return "LD B, " + r;
            case 0x55: return "LD [I], " + r;
            case 0x65: return "LD " + r + ", [I]";
            default: return data(op);
        }
    }

    private static String reg(int r) {
        return "V" + Integer.toHexString(r).toUpperCase();
    }

    private static String addr(int value) {
        return String.format("0x%03X", value);
    }

    private static String hexByte(int value) {
        return String.format("0x%02X", value);
    }

    private static String data(int op) {
        return String.format("DW 0x%04X", op);
    }
}
