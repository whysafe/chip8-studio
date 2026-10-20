package ru.whysafe.chip8.asm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AssemblerTest {

    private static int[] words(String source) throws AsmException {
        byte[] rom = Assembler.assemble(source);
        int[] words = new int[rom.length / 2];
        for (int i = 0; i < words.length; i++) {
            words[i] = ((rom[i * 2] & 0xFF) << 8) | (rom[i * 2 + 1] & 0xFF);
        }
        return words;
    }

    private static AsmException error(String source) {
        return assertThrows(AsmException.class, () -> Assembler.assemble(source));
    }

    @Test
    void encodesEveryInstructionForm() throws AsmException {
        String source = String.join("\n",
            "CLS", "RET", "SYS 0x123", "JP 0x456", "JP V0, 0x789", "CALL 0xABC",
            "SE V1, 0x22", "SE V1, V2", "SNE V3, 0x44", "SNE V3, V4",
            "LD V5, 0x66", "LD V5, V6", "LD I, 0x777", "LD V7, DT", "LD V8, K", "LD DT, V9",
            "LD ST, VA", "LD F, VB", "LD B, VC", "LD [I], VD", "LD VE, [I]",
            "ADD V1, 0x10", "ADD V1, V2", "ADD I, V3",
            "OR V1, V2", "AND V1, V2", "XOR V1, V2", "SUB V1, V2", "SHR V1, V2", "SUBN V1, V2", "SHL V1, V2",
            "RND V4, 0xF0", "DRW V5, V6, 7", "SKP V7", "SKNP V8");
        int[] expected = {
            0x00E0, 0x00EE, 0x0123, 0x1456, 0xB789, 0x2ABC,
            0x3122, 0x5120, 0x4344, 0x9340,
            0x6566, 0x8560, 0xA777, 0xF707, 0xF80A, 0xF915,
            0xFA18, 0xFB29, 0xFC33, 0xFD55, 0xFE65,
            0x7110, 0x8124, 0xF31E,
            0x8121, 0x8122, 0x8123, 0x8125, 0x8126, 0x8127, 0x812E,
            0xC4F0, 0xD567, 0xE79E, 0xE8A1};
        assertArrayEquals(expected, words(source));
    }

    @Test
    void resolvesForwardAndBackwardLabels() throws AsmException {
        String source = "start: JP end\n  CALL start\nend:\n  JP start";
        assertArrayEquals(new int[] {0x1204, 0x2200, 0x1200}, words(source));
        Map<String, Integer> symbols = Assembler.symbols(source);
        assertEquals(0x204, symbols.get("END"));
    }

    @Test
    void namesAreCaseInsensitiveAndMnemonicsToo() throws AsmException {
        assertArrayEquals(new int[] {0x1200}, words("Loop: jp LOOP"));
        assertArrayEquals(new int[] {0x6A0F}, words("ld va, 15"));
    }

    @Test
    void constantsAndExpressions() throws AsmException {
        String source = "WIDTH EQU 64\nSIZE EQU WIDTH - 8\nLD V0, SIZE\nLD I, data + 2\nLD V1, -1\ndata: DB 1, 2, 3";
        assertArrayEquals(new int[] {0x6038, 0xA208, 0x61FF}, java.util.Arrays.copyOf(words(source), 3));
    }

    @Test
    void numberFormats() throws AsmException {
        byte[] rom = Assembler.assemble("DB 255, 0xFE, #FD, 0b11111100");
        assertArrayEquals(new byte[] {(byte) 255, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC}, rom);
    }

    @Test
    void dataDirectivesLayOutBytesAndWords() throws AsmException {
        byte[] rom = Assembler.assemble("DB 1, 2, 3\nafter: DW 0x1234, after");
        assertArrayEquals(new byte[] {1, 2, 3, 0x12, 0x34, 0x02, 0x03}, rom);
    }

    @Test
    void shiftShorthandUsesSameRegister() throws AsmException {
        assertArrayEquals(new int[] {0x8336, 0x844E}, words("SHR V3\nSHL V4"));
    }

    @Test
    void commentsAndBlankLinesAreIgnored() throws AsmException {
        assertArrayEquals(new int[] {0x00E0}, words("; header\n\n   CLS   ; clear\n; trailer"));
    }

    @Test
    void errorsNameTheLine() {
        AsmException unknown = error("CLS\n\nFOO V1");
        assertEquals(3, unknown.line());
        assertTrue(unknown.getMessage().contains("FOO"));

        assertEquals(2, error("CLS\nJP nowhere").line());
        assertEquals(1, error("LD V0, 256").line());
        assertEquals(1, error("DRW V0, V1, 16").line());
        assertEquals(1, error("ADD V0").line());
        assertEquals(1, error("LD VG, 1").line());
        assertEquals(2, error("x: CLS\nx: CLS").line());
        assertEquals(1, error("JP V1, 0x200").line());
        assertEquals(1, error("DB").line());
        assertEquals(1, error("LD V0, 1 +").line());
        assertEquals(1, error("I: CLS").line());
    }

    @Test
    void constantsCannotReferToLaterNames() {
        AsmException e = error("A EQU B\nB EQU 1");
        assertEquals(1, e.line());
    }

    @Test
    void rejectsProgramsThatDoNotFitInMemory() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 1793; i++) {
            source.append("CLS\n");
        }
        assertTrue(error(source.toString()).getMessage().contains("не помещается"));
    }
}
