package ru.whysafe.chip8.asm;

/** A source error that points at the offending line. */
public final class AsmException extends Exception {

    private final int line;

    public AsmException(int line, String message) {
        super("строка " + line + ": " + message);
        this.line = line;
    }

    /** 1-based line number in the source. */
    public int line() {
        return line;
    }
}
