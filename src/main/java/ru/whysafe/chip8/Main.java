package ru.whysafe.chip8;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;

import ru.whysafe.chip8.asm.AsmException;
import ru.whysafe.chip8.asm.Assembler;
import ru.whysafe.chip8.asm.Disassembler;
import ru.whysafe.chip8.core.Chip8;
import ru.whysafe.chip8.core.EmulationException;
import ru.whysafe.chip8.core.Quirks;
import ru.whysafe.chip8.ui.Studio;

/** Command-line entry point: without arguments it opens the studio window. */
public final class Main {

    /** The window runs 700 instructions per second against 60 timer ticks; the headless runner keeps that ratio. */
    private static final int CYCLES_PER_TIMER_TICK = 12;

    private static final String USAGE = String.join("\n",
        "CHIP-8 Studio — эмулятор, отладчик и ассемблер CHIP-8",
        "",
        "  chip8-studio [файл]                      открыть окно (файл .ch8 или .c8s)",
        "  chip8-studio asm <src.c8s> [-o out.ch8]  собрать программу",
        "  chip8-studio disasm <rom.ch8>            дизассемблировать ROM",
        "  chip8-studio run <rom|src|demo:имя> [--cycles N] [--seed S] [--quirks vip|modern]",
        "                                           выполнить без окна и вывести экран",
        "  chip8-studio demos                       список встроенных демо");

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        int code;
        try {
            code = run(args, out);
        } catch (AsmException | EmulationException | IllegalArgumentException e) {
            System.err.println("Ошибка: " + e.getMessage());
            code = 1;
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
            code = 1;
        }
        if (code >= 0) {
            System.exit(code);
        }
    }

    /** Returns the process exit code, or -1 when a window was opened and the JVM must stay alive. */
    static int run(String[] args, PrintStream out) throws IOException, AsmException {
        if (args.length == 0) {
            Studio.open(null);
            return -1;
        }
        switch (args[0]) {
            case "-h":
            case "--help":
            case "help":
                out.println(USAGE);
                return 0;
            case "asm":
                return assemble(args, out);
            case "disasm":
                requireArgs(args, 2);
                out.print(Disassembler.toSource(Files.readAllBytes(Paths.get(args[1])), Chip8.PROGRAM_START, args[1]));
                return 0;
            case "run":
                return runHeadless(args, out);
            case "demos":
                for (Demos.Demo demo : Demos.all()) {
                    out.printf("  demo:%-8s %s — %s%n", demo.key, demo.title, demo.hint);
                }
                return 0;
            case "screenshot":
                // Used to render screenshot.png for the README.
                requireArgs(args, 3);
                int frames = args.length > 3 ? Integer.parseInt(args[3]) : 120;
                Studio.screenshot(Demos.find(args[1]), new File(args[2]), frames);
                return 0;
            default:
                Studio.open(new File(args[0]));
                return -1;
        }
    }

    private static int assemble(String[] args, PrintStream out) throws IOException, AsmException {
        requireArgs(args, 2);
        Path source = Paths.get(args[1]);
        Path target = args.length >= 4 && args[2].equals("-o")
            ? Paths.get(args[3])
            : Paths.get(source.toString().replaceAll("\\.c8s$", "") + ".ch8");
        byte[] rom = Assembler.assemble(new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
        Files.write(target, rom);
        out.printf("%s -> %s (%d байт)%n", source, target, rom.length);
        return 0;
    }

    private static int runHeadless(String[] args, PrintStream out) throws IOException, AsmException {
        requireArgs(args, 2);
        long cycles = 2000;
        long seed = 8;
        Quirks quirks = Quirks.COSMAC_VIP;
        for (int i = 2; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("у параметра " + args[i] + " нет значения");
            }
            switch (args[i]) {
                case "--cycles":
                    cycles = Long.parseLong(args[i + 1]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[i + 1]);
                    break;
                case "--quirks":
                    quirks = Quirks.byKey(args[i + 1]);
                    break;
                default:
                    throw new IllegalArgumentException("неизвестный параметр " + args[i]);
            }
        }

        Chip8 chip = new Chip8(new Random(seed));
        chip.setQuirks(quirks);
        chip.load(loadProgram(args[1]));
        for (long n = 0; n < cycles && !chip.isWaitingForKey(); n++) {
            chip.step();
            if (n % CYCLES_PER_TIMER_TICK == CYCLES_PER_TIMER_TICK - 1) {
                chip.tickTimers();
            }
        }
        out.print(chip.display().toAscii('#', '.'));
        out.printf("PC=%03X I=%03X циклов=%d%s%n", chip.pc(), chip.indexRegister(), chip.cycles(),
            chip.isWaitingForKey() ? " (ждёт клавишу)" : "");
        return 0;
    }

    /** Accepts a binary ROM, an assembler source or a bundled demo ("demo:maze"). */
    public static byte[] loadProgram(String spec) throws IOException, AsmException {
        if (spec.startsWith("demo:")) {
            return Assembler.assemble(Demos.find(spec.substring(5)).source());
        }
        byte[] bytes = Files.readAllBytes(Paths.get(spec));
        if (spec.toLowerCase(Locale.ROOT).endsWith(".c8s")) {
            return Assembler.assemble(new String(bytes, StandardCharsets.UTF_8));
        }
        return bytes;
    }

    private static void requireArgs(String[] args, int count) {
        if (args.length < count) {
            throw new IllegalArgumentException("не хватает аргументов\n\n" + USAGE);
        }
    }
}
