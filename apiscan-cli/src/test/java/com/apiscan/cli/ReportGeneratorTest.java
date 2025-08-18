package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportGeneratorTest {
    
    private ReportGenerator reportGenerator;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outputStream;
    
    @BeforeEach
    void setUp() {
        reportGenerator = new ReportGenerator();
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }
    
    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }
    
    @Test
    void testPrintSummaryWithBasicInformation() {
        // Create test data
        ScanResult result = createTestScanResult();
        
        // Generate report
        reportGenerator.printSummary(result, 1500L);
        
        // Verify output contains expected sections
        String output = outputStream.toString();
        
        // Check for header formatting
        assertTrue(output.contains("SCAN SUMMARY"), "Should contain formatted header");
        assertTrue(output.contains("========================================================="), "Should contain ASCII box formatting");
        
        // Check for project information
        assertTrue(output.contains("test-project"), "Should contain project name");
        assertTrue(output.contains("Spring"), "Should contain framework name");
        assertTrue(output.contains("1,500 ms"), "Should contain formatted duration");
        assertTrue(output.contains("3"), "Should contain endpoint count");
    }
    
    @Test
    void testPrintSummaryWithHTTPMethodBreakdown() {
        ScanResult result = createTestScanResult();
        
        reportGenerator.printSummary(result, 1000L);
        
        String output = outputStream.toString();
        
        // Check for HTTP method section
        assertTrue(output.contains("HTTP Methods"), "Should contain HTTP methods section");
        assertTrue(output.contains("GET"), "Should contain GET methods");
        assertTrue(output.contains("POST"), "Should contain POST methods");
        assertTrue(output.contains("DELETE"), "Should contain DELETE methods");
        
        // Check for method icons
        assertTrue(output.contains("[GET]"), "Should contain GET icon");
        assertTrue(output.contains("[POST]"), "Should contain POST icon");
        assertTrue(output.contains("[DEL]"), "Should contain DELETE icon");
    }
    
    @Test
    void testPrintSummaryWithControllerBreakdown() {
        ScanResult result = createTestScanResult();
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Check for controller section
        assertTrue(output.contains("Controllers"), "Should contain controllers section");
        assertTrue(output.contains("UserController"), "Should contain UserController");
        assertTrue(output.contains("ProductController"), "Should contain ProductController");
    }
    
    @Test
    void testPrintSummaryWithDetailedEndpoints() {
        ScanResult result = createTestScanResult();
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Check for endpoints section
        assertTrue(output.contains("API ENDPOINTS"), "Should contain endpoints section");
        assertTrue(output.contains("Controller: UserController"), "Should contain controller grouping");
        assertTrue(output.contains("/api/users"), "Should contain endpoint paths");
        assertTrue(output.contains("Parameters:"), "Should contain parameter information");
    }
    
    @Test
    void testPrintSummaryWithDeprecatedEndpoints() {
        ScanResult result = new ScanResult();
        result.setProjectPath("C:\\test\\project");
        result.setFramework("Spring");
        result.setFilesScanned(10);
        result.setScanDurationMs(800L);
        
        // Create deprecated endpoint
        ApiEndpoint deprecatedEndpoint = new ApiEndpoint();
        deprecatedEndpoint.setPath("/api/deprecated");
        deprecatedEndpoint.setHttpMethod("GET");
        deprecatedEndpoint.setMethodName("deprecatedMethod");
        deprecatedEndpoint.setControllerClass("DeprecatedController");
        deprecatedEndpoint.setDeprecated(true);
        
        result.setEndpoints(Arrays.asList(deprecatedEndpoint));
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Check for deprecated section
        assertTrue(output.contains("Deprecated Endpoints"), "Should contain deprecated section");
        assertTrue(output.contains("[DEPRECATED]"), "Should mark deprecated endpoints");
    }
    
    @Test
    void testPrintSummaryWithErrorsAndWarnings() {
        ScanResult result = createTestScanResult();
        result.addError("Test error message");
        result.addWarning("Test warning message");
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Check for errors and warnings
        assertTrue(output.contains("Errors encountered"), "Should contain errors section");
        assertTrue(output.contains("Test error message"), "Should contain error message");
        assertTrue(output.contains("Warnings"), "Should contain warnings section");
        assertTrue(output.contains("Test warning message"), "Should contain warning message");
    }
    
    @Test
    void testPrintSummaryFormattingConsistency() {
        ScanResult result = createTestScanResult();
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Check for consistent formatting
        assertTrue(output.contains("========================================================="), "Should use ASCII box formatting");
        assertTrue(output.contains("-----------------------------------------"), "Should use ASCII underlines");
        
        // Check completion message
        assertTrue(output.contains("SCAN COMPLETED SUCCESSFULLY"), "Should contain success message");
    }
    
    @Test
    void testEmptyResultHandling() {
        ScanResult emptyResult = new ScanResult();
        emptyResult.setProjectPath("C:\\empty\\project");
        emptyResult.setFramework("Unknown");
        emptyResult.setFilesScanned(0);
        emptyResult.setScanDurationMs(100L);
        
        // Should not throw exception
        assertDoesNotThrow(() -> reportGenerator.printSummary(emptyResult));
        
        String output = outputStream.toString();
        assertTrue(output.contains("SCAN SUMMARY"), "Should still show summary header");
        assertTrue(output.contains("0"), "Should show zero endpoints");
    }
    
    @Test
    void testLongControllerNamesTruncation() {
        ScanResult result = new ScanResult();
        result.setProjectPath("C:\\test");
        result.setFramework("Spring");
        result.setFilesScanned(1);
        result.setScanDurationMs(500L);
        
        // Create endpoint with very long controller name
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/test");
        endpoint.setHttpMethod("GET");
        endpoint.setMethodName("test");
        endpoint.setControllerClass("VeryLongControllerNameThatExceedsTheDisplayWidth");
        
        result.setEndpoints(Arrays.asList(endpoint));
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        
        // Should handle long names gracefully
        assertTrue(output.contains("VeryLongControllerNameThatExceedsTheDisplay") || output.contains("..."), 
            "Should truncate long controller names or handle them gracefully");
    }
    
    @Test
    void testProfessionalIndentationFormatting() {
        // Test that indentation is consistent and professional
        ScanResult result = createTestScanResult();
        
        // Add endpoint with parameters and request body
        ApiEndpoint complexEndpoint = new ApiEndpoint();
        complexEndpoint.setPath("/api/owners/{ownerId}");
        complexEndpoint.setHttpMethod("PUT");
        complexEndpoint.setMethodName("updateOwner");
        complexEndpoint.setControllerClass("OwnerController");
        
        ApiEndpoint.Parameter ownerIdParam = new ApiEndpoint.Parameter();
        ownerIdParam.setName("ownerId");
        ownerIdParam.setIn("path");
        ownerIdParam.setRequired(true);
        complexEndpoint.setParameters(Arrays.asList(ownerIdParam));
        
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        requestBody.setRequired(true);
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("OwnerFieldsDto");
        requestBody.getContent().put("application/json", mediaType);
        complexEndpoint.setRequestBody(requestBody);
        
        // Create a mutable list with the new endpoint
        List<ApiEndpoint> endpoints = new ArrayList<>(result.getEndpoints());
        endpoints.add(complexEndpoint);
        result.setEndpoints(endpoints);
        
        reportGenerator.printSummary(result);
        
        String output = outputStream.toString();
        String[] lines = output.split("\n");
        
        // Verify proper alignment in endpoint details
        boolean foundParameterLine = false;
        boolean foundRequestBodyLine = false;
        
        for (String line : lines) {
            if (line.contains("Parameters:")) {
                foundParameterLine = true;
                // Check that Parameters line has consistent indentation (11 spaces to align under URL)
                assertTrue(line.startsWith("           Parameters:"), 
                    "Parameters line should have 11-space indentation to align under URL");
            }
            if (line.contains("Request Body:")) {
                foundRequestBodyLine = true;
                // Check that Request Body line has consistent indentation (11 spaces to align under URL)
                assertTrue(line.startsWith("           Request Body:"), 
                    "Request Body line should have 11-space indentation to align under URL");
            }
        }
        
        assertTrue(foundParameterLine, "Should display parameters with proper indentation");
        assertTrue(foundRequestBodyLine, "Should display request body with proper indentation");
        
        // Verify HTTP method formatting is consistent
        assertTrue(output.contains("[GET]") || output.contains("[POST]") || output.contains("[PUT]") || output.contains("[DEL]"),
            "Should use consistent method icons");
    }
    
    private ScanResult createTestScanResult() {
        ScanResult result = new ScanResult();
        result.setProjectPath("C:\\test\\test-project");
        result.setFramework("Spring");
        result.setFilesScanned(25);
        result.setScanDurationMs(1200L);
        
        // Create test endpoints
        ApiEndpoint getUserEndpoint = new ApiEndpoint();
        getUserEndpoint.setPath("/api/users/{id}");
        getUserEndpoint.setHttpMethod("GET");
        getUserEndpoint.setMethodName("getUser");
        getUserEndpoint.setControllerClass("UserController");
        
        ApiEndpoint.Parameter idParam = new ApiEndpoint.Parameter();
        idParam.setName("id");
        idParam.setIn("path");
        idParam.setRequired(true);
        getUserEndpoint.setParameters(Arrays.asList(idParam));
        
        ApiEndpoint createUserEndpoint = new ApiEndpoint();
        createUserEndpoint.setPath("/api/users");
        createUserEndpoint.setHttpMethod("POST");
        createUserEndpoint.setMethodName("createUser");
        createUserEndpoint.setControllerClass("UserController");
        
        ApiEndpoint deleteProductEndpoint = new ApiEndpoint();
        deleteProductEndpoint.setPath("/api/products/{id}");
        deleteProductEndpoint.setHttpMethod("DELETE");
        deleteProductEndpoint.setMethodName("deleteProduct");
        deleteProductEndpoint.setControllerClass("ProductController");
        
        result.setEndpoints(Arrays.asList(getUserEndpoint, createUserEndpoint, deleteProductEndpoint));
        
        return result;
    }
}