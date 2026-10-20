package ru.whysafe.chip8.asm;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.whysafe.chip8.core.Chip8;

/**
 * Two-pass assembler for the classic CHIP-8 mnemonics (Cowgod's reference syntax).
 * <p>
 * The first pass lays out addresses and collects labels and {@code EQU} constants,
 * the second pass encodes instructions, so labels may be used before they are defined.
 * Names are case-insensitive. Expressions are sums and differences of numbers and names:
 * {@code LD I, sprites + 8}.
 */
public final class Assembler {

    private static final Pattern LABEL = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$");
    private static final Pattern CONSTANT = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s+(?i:EQU)\\s+(.+)$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern REGISTER = Pattern.compile("^V[0-9A-F]$");
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "I", "DT", "ST", "K", "F", "B", "V0", "V1", "V2", "V3", "V4", "V5", "V6", "V7",
        "V8", "V9", "VA", "VB", "VC", "VD", "VE", "VF"));

    private static final class Statement {
        final int line;
        final String mnemonic;
        final List<String> args;
        final int address;

        Statement(int line, String mnemonic, List<String> args, int address) {
            this.line = line;
            this.mnemonic = mnemonic;
            this.args = args;
            this.address = address;
        }
    }

    private final Map<String, Integer> symbols = new HashMap<>();
    private final List<Statement> statements = new ArrayList<>();
    private final int origin;

    private Assembler(int origin) {
        this.origin = origin;
    }

    public static byte[] assemble(String source) throws AsmException {
        return new Assembler(Chip8.PROGRAM_START).run(source);
    }

    /** Address of every label after assembling, useful for the debugger and for tests. */
    public static Map<String, Integer> symbols(String source) throws AsmException {
        Assembler assembler = new Assembler(Chip8.PROGRAM_START);
        assembler.layout(source);
        return new HashMap<>(assembler.symbols);
    }

    private byte[] run(String source) throws AsmException {
        int end = layout(source);
        if (end - origin > Chip8.MAX_PROGRAM_SIZE) {
            throw new AsmException(statements.get(statements.size() - 1).line,
                "программа не помещается в память: " + (end - origin) + " байт из " + Chip8.MAX_PROGRAM_SIZE);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(end - origin);
        for (Statement statement : statements) {
            emit(statement, out);
        }
        return out.toByteArray();
    }

    // ---- pass 1 -------------------------------------------------------------------------

    private int layout(String source) throws AsmException {
        String[] lines = source.split("\r?\n", -1);
        int address = origin;
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String text = stripComment(lines[i]).trim();

            Matcher label = LABEL.matcher(text);
            if (label.matches()) {
                define(label.group(1), address, lineNo);
                text = label.group(2).trim();
            }
            if (text.isEmpty()) {
                continue;
            }

            Matcher constant = CONSTANT.matcher(text);
            if (constant.matches()) {
                define(constant.group(1), evaluate(constant.group(2), lineNo), lineNo);
                continue;
            }

            String[] parts = text.split("\\s+", 2);
            String mnemonic = parts[0].toUpperCase(Locale.ROOT);
            List<String> args = parts.length > 1 ? splitArgs(parts[1]) : new ArrayList<String>();
            statements.add(new Statement(lineNo, mnemonic, args, address));
            address += sizeOf(mnemonic, args, lineNo);
        }
        return address;
    }

    private static int sizeOf(String mnemonic, List<String> args, int line) throws AsmException {
        if (mnemonic.equals("DB") || mnemonic.equals("DW")) {
            if (args.isEmpty()) {
                throw new AsmException(line, mnemonic + " без данных");
            }
            return mnemonic.equals("DB") ? args.size() : args.size() * 2;
        }
        return 2;
    }

    private void define(String name, int value, int line) throws AsmException {
        String key = name.toUpperCase(Locale.ROOT);
        if (RESERVED.contains(key)) {
            throw new AsmException(line, "имя " + name + " зарезервировано за регистром");
        }
        if (symbols.containsKey(key)) {
            throw new AsmException(line, "имя " + name + " уже определено");
        }
        symbols.put(key, value);
    }

    private static String stripComment(String line) {
        int semicolon = line.indexOf(';');
        return semicolon < 0 ? line : line.substring(0, semicolon);
    }

    private static List<String> splitArgs(String text) {
        List<String> args = new ArrayList<>();
        for (String arg : text.split(",", -1)) {
            args.add(arg.trim());
        }
        return args;
    }

    // ---- pass 2 -------------------------------------------------------------------------

    private void emit(Statement s, ByteArrayOutputStream out) throws AsmException {
        if (s.mnemonic.equals("DB")) {
            for (String arg : s.args) {
                out.write(byteValue(arg, s.line));
            }
            return;
        }
        if (s.mnemonic.equals("DW")) {
            for (String arg : s.args) {
                int word = bounded(arg, 0, 0xFFFF, "слово", s.line);
                out.write(word >>> 8);
                out.write(word & 0xFF);
            }
            return;
        }
        int opcode = encode(s);
        out.write(opcode >>> 8);
        out.write(opcode & 0xFF);
    }

    private int encode(Statement s) throws AsmException {
        List<String> a = s.args;
        int line = s.line;
        switch (s.mnemonic) {
            case "CLS":
                arity(s, 0);
                return 0x00E0;
            case "RET":
                arity(s, 0);
                return 0x00EE;
            case "SYS":
                arity(s, 1);
                return address(a.get(0), line);
            case "JP":
                if (a.size() == 2) {
                    if (!upper(a.get(0)).equals("V0")) {
                        throw new AsmException(line, "косвенный переход возможен только через V0");
                    }
                    return 0xB000 | address(a.get(1), line);
                }
                arity(s, 1);
                return 0x1000 | address(a.get(0), line);
            case "CALL":
                arity(s, 1);
                return 0x2000 | address(a.get(0), line);
            case "SE":
                return compare(s, 0x5000, 0x3000);
            case "SNE":
                return compare(s, 0x9000, 0x4000);
            case "LD":
                return encodeLoad(s);
            case "ADD":
                arity(s, 2);
                if (upper(a.get(0)).equals("I")) {
                    return 0xF01E | reg(a.get(1), line) << 8;
                }
                if (isRegister(a.get(1))) {
                    return pair(0x8004, a, line);
                }
                return 0x7000 | reg(a.get(0), line) << 8 | byteValue(a.get(1), line);
            case "OR":
                return pairOnly(s, 0x8001);
            case "AND":
                return pairOnly(s, 0x8002);
            case "XOR":
                return pairOnly(s, 0x8003);
            case "SUB":
                return pairOnly(s, 0x8005);
            case "SUBN":
                return pairOnly(s, 0x8007);
            case "SHR":
                return shift(s, 0x8006);
            case "SHL":
                return shift(s, 0x800E);
            case "RND":
                arity(s, 2);
                return 0xC000 | reg(a.get(0), line) << 8 | byteValue(a.get(1), line);
            case "DRW":
                arity(s, 3);
                return 0xD000 | reg(a.get(0), line) << 8 | reg(a.get(1), line) << 4
                    | bounded(a.get(2), 0, 15, "высота спрайта", line);
            case "SKP":
                arity(s, 1);
                return 0xE09E | reg(a.get(0), line) << 8;
            case "SKNP":
                arity(s, 1);
                return 0xE0A1 | reg(a.get(0), line) << 8;
            default:
                throw new AsmException(line, "неизвестная команда " + s.mnemonic);
        }
    }

    private int encodeLoad(Statement s) throws AsmException {
        arity(s, 2);
        int line = s.line;
        String dst = upper(s.args.get(0));
        String src = upper(s.args.get(1));

        switch (dst) {
            case "I":
                return 0xA000 | address(s.args.get(1), line);
            case "DT":
                return 0xF015 | reg(src, line) << 8;
            case "ST":
                return 0xF018 | reg(src, line) << 8;
            case "F":
                return 0xF029 | reg(src, line) << 8;
            case "B":
                return 0xF033 | reg(src, line) << 8;
            case "[I]":
                return 0xF055 | reg(src, line) << 8;
            default:
                break;
        }

        int x = reg(dst, line) << 8;
        switch (src) {
            case "DT":
                return 0xF007 | x;
            case "K":
                return 0xF00A | x;
            case "[I]":
                return 0xF065 | x;
            default:
                if (isRegister(src)) {
                    return 0x8000 | x | reg(src, line) << 4;
                }
                return 0x6000 | x | byteValue(s.args.get(1), line);
        }
    }

    private int compare(Statement s, int registerForm, int byteForm) throws AsmException {
        arity(s, 2);
        if (isRegister(s.args.get(1))) {
            return pair(registerForm, s.args, s.line);
        }
        return byteForm | reg(s.args.get(0), s.line) << 8 | byteValue(s.args.get(1), s.line);
    }

    private int pairOnly(Statement s, int base) throws AsmException {
        arity(s, 2);
        return pair(base, s.args, s.line);
    }

    /** SHR VX alone is accepted as shorthand for SHR VX, VX. */
    private int shift(Statement s, int base) throws AsmException {
        if (s.args.size() == 1) {
            int x = reg(s.args.get(0), s.line);
            return base | x << 8 | x << 4;
        }
        return pairOnly(s, base);
    }

    private static int pair(int base, List<String> args, int line) throws AsmException {
        return base | reg(args.get(0), line) << 8 | reg(args.get(1), line) << 4;
    }

    private static void arity(Statement s, int expected) throws AsmException {
        if (s.args.size() != expected) {
            throw new AsmException(s.line, s.mnemonic + " ожидает операндов: " + expected + ", указано: " + s.args.size());
        }
    }

    private static boolean isRegister(String arg) {
        return REGISTER.matcher(upper(arg)).matches();
    }

    private static int reg(String arg, int line) throws AsmException {
        String name = upper(arg);
        if (!REGISTER.matcher(name).matches()) {
            throw new AsmException(line, "ожидался регистр V0–VF, а не «" + arg + "»");
        }
        return Character.digit(name.charAt(1), 16);
    }

    private int address(String arg, int line) throws AsmException {
        return bounded(arg, 0, 0xFFF, "адрес", line);
    }

    /** Bytes accept -128..255 so that "ADD V0, -1" reads naturally. */
    private int byteValue(String arg, int line) throws AsmException {
        return bounded(arg, -128, 255, "байт", line) & 0xFF;
    }

    private int bounded(String arg, int min, int max, String what, int line) throws AsmException {
        int value = evaluate(arg, line);
        if (value < min || value > max) {
            throw new AsmException(line, String.format("%s %d вне диапазона %d..%d", what, value, min, max));
        }
        return value;
    }

    private int evaluate(String expression, int line) throws AsmException {
        String text = expression.replaceAll("\\s+", "");
        if (text.isEmpty()) {
            throw new AsmException(line, "пропущен операнд");
        }
        char last = text.charAt(text.length() - 1);
        if (last == '+' || last == '-') {
            throw new AsmException(line, "выражение обрывается на «" + last + "»");
        }
        int total = 0;
        int sign = 1;
        int pos = 0;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '+' || c == '-') {
                sign = c == '-' ? -sign : sign;
                pos++;
                continue;
            }
            int end = pos;
            while (end < text.length() && text.charAt(end) != '+' && text.charAt(end) != '-') {
                end++;
            }
            total += sign * term(text.substring(pos, end), line);
            sign = 1;
            pos = end;
        }
        return total;
    }

    private int term(String token, int line) throws AsmException {
        String lower = token.toLowerCase(Locale.ROOT);
        try {
            if (lower.startsWith("0x")) {
                return Integer.parseInt(lower.substring(2), 16);
            }
            if (lower.startsWith("#")) {
                return Integer.parseInt(lower.substring(1), 16);
            }
            if (lower.startsWith("0b")) {
                return Integer.parseInt(lower.substring(2), 2);
            }
            if (Character.isDigit(lower.charAt(0))) {
                return Integer.parseInt(lower);
            }
        } catch (NumberFormatException e) {
            throw new AsmException(line, "не число: " + token);
        }
        if (!NAME.matcher(token).matches()) {
            throw new AsmException(line, "непонятный операнд «" + token + "»");
        }
        Integer value = symbols.get(token.toUpperCase(Locale.ROOT));
        if (value == null) {
            throw new AsmException(line, "неизвестная метка или константа " + token);
        }
        return value;
    }

    private static String upper(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }
}
