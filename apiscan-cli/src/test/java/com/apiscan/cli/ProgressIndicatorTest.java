package com.apiscan.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class ProgressIndicatorTest {
    
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;
    
    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }
    
    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }
    
    @Test
    void testProgressIndicatorStartsAndStops() throws InterruptedException {
        ProgressIndicator indicator = new ProgressIndicator("[INFO] Testing...");
        
        // Start the indicator
        indicator.start();
        
        // Let it run for a short time
        Thread.sleep(250);
        
        // Stop the indicator
        indicator.stop();
        
        // Give it time to clean up
        Thread.sleep(100);
        
        String output = outputStream.toString();
        
        // Should contain the message
        assertTrue(output.contains("[INFO] Testing..."), "Output should contain the progress message");
        
        // Should have some spinner characters (ASCII on Windows)
        assertTrue(output.contains("|") || output.contains("/") || 
                  output.contains("-") || output.contains("\\") ||
                  output.contains("⠋") || output.contains("⠙"),
                  "Output should contain spinner characters");
    }
    
    @Test
    void testProgressIndicatorWithCompletionMessage() throws InterruptedException {
        ProgressIndicator indicator = new ProgressIndicator("[INFO] Processing...");
        
        // Start the indicator
        indicator.start();
        
        // Let it run briefly
        Thread.sleep(150);
        
        // Complete with a message
        indicator.complete("[SUCCESS] Processing completed");
        
        // Give it time to print
        Thread.sleep(50);
        
        String output = outputStream.toString();
        
        // Should contain completion message
        assertTrue(output.contains("[SUCCESS] Processing completed"), 
                  "Output should contain the completion message");
    }
    
    @Test
    void testMultipleStartCallsHandledGracefully() throws InterruptedException {
        ProgressIndicator indicator = new ProgressIndicator("[INFO] Testing multiple starts...");
        
        // Start multiple times - should be handled gracefully
        indicator.start();
        indicator.start();
        indicator.start();
        
        Thread.sleep(150);
        
        indicator.stop();
        
        // Should not throw any exceptions
        assertTrue(true, "Multiple start calls should be handled gracefully");
    }
    
    @Test
    void testMultipleStopCallsHandledGracefully() {
        ProgressIndicator indicator = new ProgressIndicator("[INFO] Testing multiple stops...");
        
        indicator.start();
        
        // Stop multiple times - should be handled gracefully
        indicator.stop();
        indicator.stop();
        indicator.stop();
        
        // Should not throw any exceptions
        assertTrue(true, "Multiple stop calls should be handled gracefully");
    }
    
    @Test
    void testStopWithoutStartHandledGracefully() {
        ProgressIndicator indicator = new ProgressIndicator("[INFO] Testing stop without start...");
        
        // Stop without starting - should be handled gracefully
        indicator.stop();
        
        // Should not throw any exceptions
        assertTrue(true, "Stop without start should be handled gracefully");
    }
}