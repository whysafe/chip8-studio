package ru.whysafe.chip8.core;

/** The program did something the machine cannot do: unknown opcode, stack overflow and so on. */
public final class EmulationException extends RuntimeException {

    private final int address;

    public EmulationException(int address, String message) {
        super(String.format("0x%03X: %s", address, message));
        this.address = address;
    }

    public int address() {
        return address;
    }
}
