package ru.whysafe.chip8.asm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class DisassemblerTest {

    @Test
    void everyOpcodeSurvivesARoundTrip() throws AsmException {
        for (int op = 0; op <= 0xFFFF; op++) {
            String text = Disassembler.decode(op);
            byte[] rom = Assembler.assemble(text);
            int back = ((rom[0] & 0xFF) << 8) | (rom[1] & 0xFF);
            assertEquals(op, back, () -> text);
        }
    }

    @Test
    void decodesReadableMnemonics() {
        assertEquals("CLS", Disassembler.decode(0x00E0));
        assertEquals("DRW V1, V2, 5", Disassembler.decode(0xD125));
        assertEquals("LD VA, [I]", Disassembler.decode(0xFA65));
        assertEquals("JP V0, 0x2F0", Disassembler.decode(0xB2F0));
        assertEquals("DW 0x8128", Disassembler.decode(0x8128));
    }

    @Test
    void listingKeepsAddressesAndTrailingByte() {
        List<Disassembler.Line> lines = Disassembler.disassemble(new byte[] {0x00, (byte) 0xE0, 0x12}, 0x200);
        assertEquals(2, lines.size());
        assertEquals(0x200, lines.get(0).address);
        assertEquals("DB 0x12", lines.get(1).text);
        assertEquals(0x202, lines.get(1).address);
    }

    @Test
    void wholeRomSourceReassemblesToTheSameBytes() throws AsmException {
        byte[] rom = new byte[301];
        new Random(42).nextBytes(rom);
        String source = Disassembler.toSource(rom, 0x200, "random.ch8");
        assertArrayEquals(rom, Assembler.assemble(source));
    }
}
