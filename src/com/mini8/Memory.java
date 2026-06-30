package com.mini8;

import java.io.FileInputStream;
import java.io.IOException;

public class Memory {

    public static final int MEMORY_SIZE = 4096; // 4K bytes
    public static final int PROGRAM_START = 0x200; // Programs are loaded at address 0x200

    private final byte[] memory;

    public Memory() {
        this.memory = new byte[MEMORY_SIZE];
    }

    /**
     * Loads a ROM file into memory starting at PROGRAM_START (0x200).
     *
     * @param filePath path to the .ch8 ROM file
     * @throws IOException if the file cannot be read
     */
    public void loadRom(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            int offset = PROGRAM_START;
            int b;
            while ((b = fis.read()) != -1 && offset < MEMORY_SIZE) {
                memory[offset++] = (byte) b;
            }
        }
    }

    /**
     * Reads a single byte from the given memory address.
     *
     * @param address memory address (0 - 4095)
     * @return the byte at that address (unsigned, 0-255)
     */
    public int readByte(int address) {
        if (address < 0 || address >= MEMORY_SIZE) {
            throw new IllegalArgumentException("Address out of bounds: " + address);
        }
        return memory[address] & 0xFF;
    }

    /**
     * Reads a 16-bit word (two bytes, big-endian) from the given memory address.
     *
     * @param address memory address (0 - 4094)
     * @return the 16-bit word at that address
     */
    public int readWord(int address) {
        int high = readByte(address);
        int low = readByte(address + 1);
        return (high << 8) | low;
    }

    /**
     * Writes a byte to the given memory address.
     *
     * @param address memory address (0 - 4095)
     * @param value   byte value (0-255)
     */
    public void writeByte(int address, int value) {
        if (address < 0 || address >= MEMORY_SIZE) {
            throw new IllegalArgumentException("Address out of bounds: " + address);
        }
        memory[address] = (byte) (value & 0xFF);
    }

    /**
     * Returns the total memory size in bytes.
     */
    public int getSize() {
        return MEMORY_SIZE;
    }

    /**
     * Clears all memory (sets all bytes to 0).
     */
    public void clear() {
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = 0;
        }
    }
}