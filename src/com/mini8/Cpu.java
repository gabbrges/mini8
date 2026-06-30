package com.mini8;

public class Cpu {

    // Registers V0 through VF (16 general-purpose 8-bit registers)
    private final int[] V;

    // Address register I (16-bit)
    private int I;

    // Program counter (16-bit)
    private int pc;

    // Stack pointer (8-bit)
    private int sp;

    // Stack (16 levels, each storing a 16-bit return address)
    private final int[] stack;

    // Delay timer (counts down at 60 Hz)
    private int delayTimer;

    // Sound timer (counts down at 60 Hz, beeps while > 0)
    private int soundTimer;

    // Reference to the system memory
    private final Memory memory;

    // Reference to the screen
    private final Screen screen;

    public Cpu(Memory memory, Screen screen) {
        this.memory = memory;
        this.screen = screen;
        this.V = new int[16];
        this.stack = new int[16];
        this.pc = Memory.PROGRAM_START; // Start execution at 0x200
        this.I = 0;
        this.sp = 0;
        this.delayTimer = 0;
        this.soundTimer = 0;
    }

    /**
     * Executes a single CPU cycle:
     * 1. Fetch the 16-bit opcode at the current PC (big-endian)
     * 2. Increment PC by 2
     * 3. Decode and execute the instruction
     */
    public void runCycle() {
        // FETCH: combine two consecutive bytes into a 16-bit opcode
        int highByte = memory.readByte(pc);
        int lowByte  = memory.readByte(pc + 1);
        int opcode   = (highByte << 8) | lowByte; // bit-shift to form 16-bit instruction

        // Increment PC to point to the next instruction
        pc += 2;

        // DECODE & EXECUTE
        execute(opcode);
    }

    /**
     * Decodes and executes the given 16-bit CHIP-8 opcode.
     */
    private void execute(int opcode) {
        // Extract common fields from the opcode
        int firstNibble  = (opcode & 0xF000) >> 12; // bits 12-15
        int x            = (opcode & 0x0F00) >> 8;  // bits 8-11  (register X)
        int y            = (opcode & 0x00F0) >> 4;  // bits 4-7   (register Y)
        int n            =  opcode & 0x000F;         // bits 0-3   (lowest nibble)
        int nn           =  opcode & 0x00FF;         // bits 0-7   (low byte)
        int nnn          =  opcode & 0x0FFF;         // bits 0-11  (12-bit address)

        switch (firstNibble) {
            case 0x0:
                // 0NNN: RCA 1802 call (ignored by most modern interpreters)
                // 00E0: Clear screen
                // 00EE: Return from subroutine
                switch (opcode & 0x00FF) {
                    case 0xE0: // 00E0: Clear screen
                        screen.clear();
                        break;
                    case 0xEE: // 00EE: Return from subroutine
                        sp--;
                        pc = stack[sp];
                        break;
                    default: // 0NNN: RCA 1802 call (ignored)
                        break;
                }
                break;

            case 0x1: // 1NNN: Jump to address NNN
                pc = nnn;
                break;

            case 0x2: // 2NNN: Call subroutine at NNN
                stack[sp] = pc;
                sp++;
                pc = nnn;
                break;

            case 0x3: // 3XNN: Skip next instruction if VX == NN
                if (V[x] == nn) {
                    pc += 2;
                }
                break;

            case 0x4: // 4XNN: Skip next instruction if VX != NN
                if (V[x] != nn) {
                    pc += 2;
                }
                break;

            case 0x5: // 5XY0: Skip next instruction if VX == VY
                if (V[x] == V[y]) {
                    pc += 2;
                }
                break;

            case 0x6: // 6XNN: Set VX = NN
                V[x] = nn;
                break;

            case 0x7: // 7XNN: Set VX = VX + NN
                V[x] = (V[x] + nn) & 0xFF;
                break;

            case 0x8: // 8XY_: Register-to-register operations
                switch (n) {
                    case 0x0: // 8XY0: Set VX = VY
                        V[x] = V[y];
                        break;
                    case 0x1: // 8XY1: Set VX = VX | VY
                        V[x] = (V[x] | V[y]) & 0xFF;
                        V[0xF] = 0;
                        break;
                    case 0x2: // 8XY2: Set VX = VX & VY
                        V[x] = (V[x] & V[y]) & 0xFF;
                        V[0xF] = 0;
                        break;
                    case 0x3: // 8XY3: Set VX = VX ^ VY
                        V[x] = (V[x] ^ V[y]) & 0xFF;
                        V[0xF] = 0;
                        break;
                    case 0x4: // 8XY4: Set VX = VX + VY, VF = carry
                        int sum = V[x] + V[y];
                        V[0xF] = (sum > 0xFF) ? 1 : 0;
                        V[x] = sum & 0xFF;
                        break;
                    case 0x5: // 8XY5: Set VX = VX - VY, VF = not borrow
                        V[0xF] = (V[x] >= V[y]) ? 1 : 0;
                        V[x] = (V[x] - V[y]) & 0xFF;
                        break;
                    case 0x6: // 8XY6: Set VX = VX >> 1, VF = LSB
                        V[0xF] = V[x] & 0x1;
                        V[x] = V[x] >> 1;
                        break;
                    case 0x7: // 8XY7: Set VX = VY - VX, VF = not borrow
                        V[0xF] = (V[y] >= V[x]) ? 1 : 0;
                        V[x] = (V[y] - V[x]) & 0xFF;
                        break;
                    case 0xE: // 8XYE: Set VX = VX << 1, VF = MSB
                        V[0xF] = (V[x] & 0x80) >> 7;
                        V[x] = (V[x] << 1) & 0xFF;
                        break;
                    default:
                        break;
                }
                break;

            case 0x9: // 9XY0: Skip next instruction if VX != VY
                if (V[x] != V[y]) {
                    pc += 2;
                }
                break;

            case 0xA: // ANNN: Set I = NNN
                I = nnn;
                break;

            case 0xB: // BNNN: Jump to address NNN + V0
                pc = nnn + V[0];
                break;

            case 0xC: // CXNN: Set VX = random byte & NN
                V[x] = (int) (Math.random() * 256) & nn;
                break;

            case 0xD: // DXYN: Draw sprite at (VX, VY) with N bytes from memory[I]
                int xPos = V[x] % Screen.WIDTH;
                int yPos = V[y] % Screen.HEIGHT;
                int rows = n;

                // Read N bytes of sprite data from memory starting at I
                byte[] spriteData = new byte[rows];
                for (int i = 0; i < rows; i++) {
                    spriteData[i] = (byte) memory.readByte(I + i);
                }

                // Draw the sprite and set VF (collision flag)
                boolean collision = screen.drawSprite(xPos, yPos, spriteData);
                V[0xF] = collision ? 1 : 0;
                break;

            case 0xE: // EX__: Key input operations
                switch (opcode & 0x00FF) {
                    case 0x9E: // EX9E: Skip if key VX is pressed (continuous hold)
                        if (screen.isKeyHeld(V[x])) {
                            pc += 2;
                        }
                        break;
                    case 0xA1: // EXA1: Skip if key VX is not pressed (continuous hold)
                        if (!screen.isKeyHeld(V[x])) {
                            pc += 2;
                        }
                        break;
                    default:
                        break;
                }
                break;

            case 0xF: // FX__: Miscellaneous operations
                switch (opcode & 0x00FF) {
                    case 0x07: // FX07: Set VX = delay timer
                        V[x] = delayTimer;
                        break;
                    case 0x0A: // FX0A: Wait for key press, store in VX
                        boolean keyFound = false;
                        for (int k = 0; k < 16; k++) {
                            if (screen.isKeyPressed(k)) {
                                V[x] = k;
                                keyFound = true;
                                break;
                            }
                        }
                        if (!keyFound) {
                            pc -= 2; // Re-run this instruction until a key is pressed
                        }
                        break;
                    case 0x15: // FX15: Set delay timer = VX
                        delayTimer = V[x];
                        break;
                    case 0x18: // FX18: Set sound timer = VX
                        soundTimer = V[x];
                        break;
                    case 0x1E: // FX1E: Set I = I + VX
                        I = I + V[x];
                        break;
                    case 0x29: // FX29: Set I = sprite location for digit VX
                        I = V[x] * 5; // Each digit sprite is 5 bytes
                        break;
                    case 0x33: // FX33: Store BCD of VX at I, I+1, I+2
                        memory.writeByte(I,     V[x] / 100);
                        memory.writeByte(I + 1, (V[x] / 10) % 10);
                        memory.writeByte(I + 2,  V[x] % 10);
                        break;
                    case 0x55: // FX55: Store V0..VX in memory starting at I
                        for (int i = 0; i <= x; i++) {
                            memory.writeByte(I + i, V[i]);
                        }
                        break;
                    case 0x65: // FX65: Read V0..VX from memory starting at I
                        for (int i = 0; i <= x; i++) {
                            V[i] = memory.readByte(I + i);
                        }
                        break;
                    default:
                        break;
                }
                break;

            default:
                break;
        }
    }

    /**
     * Decrements the delay and sound timers by 1 each.
     * Should be called at 60 Hz.
     */
    public void tickTimers() {
        if (delayTimer > 0) delayTimer--;
        if (soundTimer > 0) soundTimer--;
    }

    // --- Getters for debugging / display ---

    public int getPc() {
        return pc;
    }

    public int getI() {
        return I;
    }

    public int[] getV() {
        return V.clone();
    }

    public int getDelayTimer() {
        return delayTimer;
    }

    public int getSoundTimer() {
        return soundTimer;
    }
}