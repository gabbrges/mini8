package com.mini8;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

/**
 * CHIP-8 Screen with key latching for proper input handling.
 * 
 * Key latching: instead of tracking continuous hold state, we track
 * whether a key was pressed *since the last frame*. Each call to
 * isKeyPressed() consumes the latch, so a single physical press
 * is registered exactly once — not 800 times per frame.
 */
public class Screen {

    public static final int WIDTH = 64;
    public static final int HEIGHT = 32;
    public static final int SCALE = 10;

    // Back buffer: 0 = black (off), non-zero = green (on)
    private final int[] backBuffer;

    // BufferedImage that the EDT draws
    private final BufferedImage image;

    // Swing components
    private final JFrame frame;
    private final ScreenPanel panel;

    // Key state: current physical hold state
    private final boolean[] keys;

    // Key latch: set to true on keyPressed, cleared on the FIRST isKeyPressed() call
    // This prevents a single physical press from being detected multiple times.
    private final boolean[] keyLatches;

    // Store previous key state for edge detection
    private final boolean[] prevKeys;

    public Screen() {
        this.backBuffer = new int[WIDTH * HEIGHT];
        this.keys = new boolean[16];
        this.keyLatches = new boolean[16];
        this.prevKeys = new boolean[16];

        this.image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        this.panel = new ScreenPanel();
        this.panel.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));

        this.frame = new JFrame("Mini8 - CHIP-8 Emulator");
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setResizable(false);
        this.frame.add(panel);
        this.frame.pack();
        this.frame.setVisible(true);

        // Key mapping:
        //   1 2 3 C     ->     1 2 3 4
        //   4 5 6 D     ->     Q W E R
        //   7 8 9 E     ->     A S D F
        //   A 0 B F     ->     Z X C V
        this.frame.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = mapKey(e.getKeyCode());
                if (key >= 0) {
                    if (!keys[key]) {
                        // Key just pressed — set the latch
                        keyLatches[key] = true;
                    }
                    keys[key] = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int key = mapKey(e.getKeyCode());
                if (key >= 0) {
                    keys[key] = false;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) { }
        });
    }

    private int mapKey(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_1 -> 0x1;
            case KeyEvent.VK_2 -> 0x2;
            case KeyEvent.VK_3 -> 0x3;
            case KeyEvent.VK_4 -> 0xC;
            case KeyEvent.VK_Q -> 0x4;
            case KeyEvent.VK_W -> 0x5;
            case KeyEvent.VK_E -> 0x6;
            case KeyEvent.VK_R -> 0xD;
            case KeyEvent.VK_A -> 0x7;
            case KeyEvent.VK_S -> 0x8;
            case KeyEvent.VK_D -> 0x9;
            case KeyEvent.VK_F -> 0xE;
            case KeyEvent.VK_Z -> 0xA;
            case KeyEvent.VK_X -> 0x0;
            case KeyEvent.VK_C -> 0xB;
            case KeyEvent.VK_V -> 0xF;
            default -> -1;
        };
    }

    public void repaintPanel() {
        panel.repaint();
    }

    /**
     * Clears the entire screen (all pixels off).
     */
    public void clear() {
        for (int i = 0; i < backBuffer.length; i++) {
            backBuffer[i] = 0;
        }
        flushBackBuffer();
        // No repaint here — repaintPanel() handles that once per frame
    }

    /**
     * Draws a sprite at (x, y) using XOR logic.
     * Only flushes pixel data to the BufferedImage.
     * Does NOT call repaint() — that happens once per frame in the game loop.
     */
    public boolean drawSprite(int x, int y, byte[] spriteData) {
        boolean collision = false;
        int colorOn = 0xFF00FF00;

        for (int row = 0; row < spriteData.length; row++) {
            int rowBits = spriteData[row] & 0xFF;
            for (int col = 0; col < 8; col++) {
                int bit = (rowBits >> (7 - col)) & 1;
                if (bit == 1) {
                    int wx = (x + col) % WIDTH;
                    int wy = (y + row) % HEIGHT;
                    int idx = wy * WIDTH + wx;
                    int old = backBuffer[idx];
                    backBuffer[idx] ^= colorOn;
                    if (old != 0 && backBuffer[idx] == 0) collision = true;
                }
            }
        }

        flushBackBuffer();
        // No repaint here — repaintPanel() handles that once per frame
        return collision;
    }

    private void flushBackBuffer() {
        image.setRGB(0, 0, WIDTH, HEIGHT, backBuffer, 0, WIDTH);
    }

    // ---- Input methods (key latching) ----

    /**
     * Returns true if the key was pressed SINCE the last call to this method.
     * The latch is consumed (reset to false) after reading.
     * This ensures each physical press generates exactly one event.
     */
    public boolean isKeyPressed(int key) {
        if (key < 0 || key > 15) return false;
        boolean latched = keyLatches[key];
        keyLatches[key] = false; // consume the latch
        return latched;
    }

    /**
     * Returns true if the key is currently held down (continuous state).
     * Used for movement/held-key checks.
     */
    public boolean isKeyHeld(int key) {
        if (key < 0 || key > 15) return false;
        return keys[key];
    }

    /**
     * Resets all key latches. Called at the start of each frame to
     * prevent stale latches from carrying over.
     */
    public void resetKeyLatches() {
        for (int i = 0; i < 16; i++) {
            keyLatches[i] = false;
        }
    }

    // ---- Inner JPanel ----

    private class ScreenPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    }
}