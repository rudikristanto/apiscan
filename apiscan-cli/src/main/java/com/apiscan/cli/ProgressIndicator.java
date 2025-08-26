package com.apiscan.cli;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple progress indicator for long-running operations.
 * Shows animated spinner in console.
 */
public class ProgressIndicator {
    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] ASCII_SPINNER = {"|", "/", "-", "\\"};
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread animationThread;
    private final String message;
    private final boolean useAscii;
    
    public ProgressIndicator(String message) {
        this.message = message;
        // Use ASCII spinner for better Windows compatibility
        this.useAscii = System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * Start the progress animation
     */
    public void start() {
        if (running.get()) {
            return;
        }
        
        running.set(true);
        animationThread = new Thread(() -> {
            String[] frames = useAscii ? ASCII_SPINNER : SPINNER_FRAMES;
            int frameIndex = 0;
            
            try {
                while (running.get()) {
                    System.out.print("\r" + message + " " + frames[frameIndex]);
                    frameIndex = (frameIndex + 1) % frames.length;
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        animationThread.setDaemon(true);
        animationThread.start();
    }
    
    /**
     * Stop the progress animation and clear the line
     */
    public void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        try {
            if (animationThread != null) {
                animationThread.join(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Clear the progress line
        System.out.print("\r" + " ".repeat(message.length() + 5) + "\r");
    }
    
    /**
     * Stop with a completion message
     */
    public void complete(String completionMessage) {
        stop();
        System.out.println(completionMessage);
    }
}