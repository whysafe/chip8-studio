package ru.whysafe.chip8.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The CHIP-8 buzzer: a 440 Hz square wave that sounds while the sound timer is non-zero.
 * Audio is streamed from its own thread in 10 ms chunks so that switching it on and off
 * from the UI thread is immediate. Without an audio device the beeper stays silent.
 */
final class Beeper {

    private static final float SAMPLE_RATE = 44100f;
    private static final int TONE_HZ = 440;
    private static final int AMPLITUDE = 22;

    private volatile boolean playing;
    private volatile boolean muted;
    private volatile boolean closed;

    Beeper() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, (int) SAMPLE_RATE / 10);
            line.start();
            Thread thread = new Thread(() -> stream(line), "chip8-beeper");
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            closed = true;
        }
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
    }

    void setMuted(boolean muted) {
        this.muted = muted;
    }

    void close() {
        closed = true;
    }

    private void stream(SourceDataLine line) {
        byte[] chunk = new byte[(int) SAMPLE_RATE / 100];
        int halfPeriod = Math.round(SAMPLE_RATE / (TONE_HZ * 2));
        long phase = 0;
        while (!closed) {
            boolean audible = playing && !muted;
            for (int i = 0; i < chunk.length; i++) {
                chunk[i] = audible ? (byte) ((phase++ / halfPeriod) % 2 == 0 ? AMPLITUDE : -AMPLITUDE) : 0;
            }
            line.write(chunk, 0, chunk.length);
        }
        line.stop();
        line.close();
    }
}
