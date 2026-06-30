package com.mini8;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * CHIP-8 sound output using javax.sound.sampled.
 * 
 * Generates a 440 Hz square wave in a background thread.
 * The sound toggles on/off via the playing flag, set by Main
 * based on the CPU's soundTimer.
 */
public class Sound {

    private static final float SAMPLE_RATE = 44100.0f;
    private static final int SAMPLE_BITS = 8;           // 8-bit unsigned
    private static final int CHANNELS = 1;               // mono
    private static final float FREQUENCY = 440.0f;       // 440 Hz (A4)

    private volatile boolean playing;
    private volatile boolean running;
    private SourceDataLine line;
    private Thread audioThread;

    public Sound() {
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_BITS,
            CHANNELS,
            true,  // signed
            false  // little-endian (irrelevant for 8-bit)
        );

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            this.line = (SourceDataLine) AudioSystem.getLine(info);
            this.line.open(format, 4096);
            this.line.start();
        } catch (LineUnavailableException e) {
            System.err.println("Sound unavailable: " + e.getMessage());
            this.line = null;
        }

        this.running = true;
        this.playing = false;

        // Start the audio thread
        this.audioThread = new Thread(this::audioLoop, "audio-loop");
        this.audioThread.setDaemon(true);
        this.audioThread.start();
    }

    /**
     * Called once per frame (60 Hz) to update the sound state.
     * @param shouldPlay true if soundTimer > 0, false otherwise
     */
    public void update(boolean shouldPlay) {
        this.playing = shouldPlay;
    }

    /**
     * Stops the audio thread and releases the line.
     */
    public void shutdown() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    /**
     * Background audio loop: generates 440 Hz square wave or silence.
     * Runs continuously, writing small buffers to the SourceDataLine.
     */
    private void audioLoop() {
        if (line == null) return;

        int period = (int) (SAMPLE_RATE / FREQUENCY); // ~100 samples per cycle
        byte[] silence = new byte[period];
        byte[] wave    = new byte[period];

        // Precompute a square wave: high half = 127, low half = -128
        for (int i = 0; i < period; i++) {
            if (i < period / 2) {
                wave[i] = 127;  // high
            } else {
                wave[i] = -128; // low
            }
        }

        // Buffer for writing to the line (multiple periods per write)
        int writeBufferSize = period * 4; // 4 cycles per write
        byte[] writeBuffer = new byte[writeBufferSize];

        while (running) {
            if (playing) {
                // Fill the write buffer with square wave samples
                for (int i = 0; i < writeBufferSize; i++) {
                    writeBuffer[i] = wave[i % period];
                }
            } else {
                // Fill with silence
                for (int i = 0; i < writeBufferSize; i++) {
                    writeBuffer[i] = 0;
                }
            }

            // Write to the audio line (blocking if buffer is full)
            line.write(writeBuffer, 0, writeBufferSize);
        }
    }
}