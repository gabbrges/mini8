package com.mini8;

import java.io.IOException;

public class Main {

    // ─── CHIP-8 font sprite data ────────────────────────────────────────────
    private static final int[] FONT_SPRITES = {
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
        0xF0, 0x80, 0xF0, 0x80, 0x80  // F
    };

    // ─── Game loop configuration ────────────────────────────────────────────

    /** How many CHIP-8 instructions to execute per frame (60 Hz).     */
    private static final int CYCLES_PER_FRAME = 25;

    /** Duration of one frame in nanoseconds: 1/60 of a second ≈ 16.67ms. */
    private static final long FRAME_TIME_NANO = 1_000_000_000L / 60;

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java com.mini8.Main <path-to-rom>");
            System.out.println("Example: java com.mini8.Main roms/Pong.ch8");
            System.exit(1);
        }

        String romPath = args[0];
        Memory memory = new Memory();
        Screen screen = new Screen();
        Cpu cpu = new Cpu(memory, screen);
        Sound sound = new Sound();

        // Shutdown audio cleanly when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(sound::shutdown, "sound-shutdown"));

        try {
            // Load font sprites into memory at 0x000 - 0x04F
            for (int i = 0; i < FONT_SPRITES.length; i++) {
                memory.writeByte(i, FONT_SPRITES[i]);
            }

            // Load the ROM into memory at 0x200
            memory.loadRom(romPath);
            System.out.println("ROM loaded: " + romPath);

            // ── Game loop ────────────────────────────────────────────────────
            // Structure:
            //   1. Wait until the next 60 Hz beat (≈16.67ms since last tick)
            //   2. Execute CYCLES_PER_FRAME instructions
            //   3. Decrement delay/sound timers once
            //   4. Update sound state
            //   5. Repaint screen
            //   6. Repeat

            long nextFrameTime = System.nanoTime();

            while (true) {
                // Sleep until the next 60 Hz tick
                long now = System.nanoTime();
                long sleepNano = nextFrameTime - now;

                if (sleepNano > 0) {
                    long sleepMs = sleepNano / 1_000_000;
                    int sleepNs  = (int) (sleepNano % 1_000_000);
                    try {
                        Thread.sleep(sleepMs, sleepNs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Schedule the next 60 Hz beat
                nextFrameTime += FRAME_TIME_NANO;

                // If we fell behind (e.g. paused in debugger), catch up
                if (nextFrameTime < System.nanoTime()) {
                    nextFrameTime = System.nanoTime();
                }

                // ── 60 Hz tick ───────────────────────────────────────────────

                // Execute this frame's batch of CPU instructions
                for (int i = 0; i < CYCLES_PER_FRAME; i++) {
                    cpu.runCycle();
                }

                // Decrement timers once per frame (exactly 60 Hz)
                cpu.tickTimers();

                // Update sound: play while soundTimer > 0, off when 0
                sound.update(cpu.getSoundTimer() > 0);

                // Render the screen
                screen.repaintPanel();
            }

        } catch (IOException e) {
            System.err.println("Error loading ROM: " + e.getMessage());
            System.exit(1);
        }
    }
}