package com.apiscan.framework.spring;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConstantResolutionTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    public void testConstantResolutionInRequestHeader() throws IOException {
        // Create a Constants class with ACCEPT field
        String constantsSource = """
            package com.example;
            
            public class Constants {
                public static final String ACCEPT = "Accept";
                public static final String CONTENT_TYPE = "Content-Type";
                public static final String AUTHORIZATION = "Authorization";
            }
            """;
        
        // Create a controller that uses constants in annotations
        String controllerSource = """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api")
            public class TestController {
                
                @GetMapping("/test1")
                public String test1(@RequestHeader(Constants.ACCEPT) String acceptHeader) {
                    return "test1";
                }
                
                @PostMapping("/test2")
                public String test2(@RequestHeader(value = Constants.CONTENT_TYPE) String contentType,
                                   @RequestHeader(Constants.AUTHORIZATION) String auth) {
                    return "test2";
                }
                
                @PutMapping("/test3")
                public String test3(@RequestParam(Constants.ACCEPT) String acceptParam) {
                    return "test3";
                }
            }
            """;
        
        // Create directory structure
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Write the files
        Files.writeString(srcDir.resolve("Constants.java"), constantsSource);
        Files.writeString(srcDir.resolve("TestController.java"), controllerSource);
        
        // Scan the project
        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);
        
        // Verify the results
        assertNotNull(result);
        assertEquals(3, result.getEndpoints().size());
        
        // Check test1 endpoint - header parameter should be resolved to "Accept"
        ApiEndpoint test1 = result.getEndpoints().stream()
            .filter(e -> e.getPath().equals("/api/test1"))
            .findFirst()
            .orElse(null);
        assertNotNull(test1);
        assertEquals(1, test1.getParameters().size());
        ApiEndpoint.Parameter acceptHeader = test1.getParameters().get(0);
        assertEquals("Accept", acceptHeader.getName());
        assertEquals("header", acceptHeader.getIn());
        
        // Check test2 endpoint - should have two header parameters with resolved names
        ApiEndpoint test2 = result.getEndpoints().stream()
            .filter(e -> e.getPath().equals("/api/test2"))
            .findFirst()
            .orElse(null);
        assertNotNull(test2);
        assertEquals(2, test2.getParameters().size());
        
        ApiEndpoint.Parameter contentType = test2.getParameters().stream()
            .filter(p -> "Content-Type".equals(p.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(contentType);
        assertEquals("header", contentType.getIn());
        
        ApiEndpoint.Parameter auth = test2.getParameters().stream()
            .filter(p -> "Authorization".equals(p.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(auth);
        assertEquals("header", auth.getIn());
        
        // Check test3 endpoint - query parameter should be resolved
        ApiEndpoint test3 = result.getEndpoints().stream()
            .filter(e -> e.getPath().equals("/api/test3"))
            .findFirst()
            .orElse(null);
        assertNotNull(test3);
        assertEquals(1, test3.getParameters().size());
        ApiEndpoint.Parameter acceptParam = test3.getParameters().get(0);
        assertEquals("Accept", acceptParam.getName());
        assertEquals("query", acceptParam.getIn());
    }
    
    @Test
    public void testConstantResolutionWithUnresolvedConstants() throws IOException {
        // Test when constants can't be resolved (e.g., from external library)
        String controllerSource = """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api")
            public class TestController {
                
                @GetMapping("/test")
                public String test(@RequestHeader(ExternalConstants.HEADER_NAME) String header) {
                    return "test";
                }
            }
            """;
        
        // Create directory structure
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Write the file
        Files.writeString(srcDir.resolve("TestController.java"), controllerSource);
        
        // Scan the project
        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);
        
        // Verify the results
        assertNotNull(result);
        assertEquals(1, result.getEndpoints().size());
        
        ApiEndpoint endpoint = result.getEndpoints().get(0);
        assertEquals(1, endpoint.getParameters().size());
        
        // When constant can't be resolved, it should keep the original value
        ApiEndpoint.Parameter param = endpoint.getParameters().get(0);
        assertEquals("ExternalConstants.HEADER_NAME", param.getName());
        assertEquals("header", param.getIn());
    }
    
    @Test
    public void testConstantResolutionInPathVariable() throws IOException {
        // Test constant resolution in path variables
        String constantsSource = """
            package com.example;
            
            public class PathConstants {
                public static final String USER_ID = "userId";
                public static final String PRODUCT_ID = "productId";
            }
            """;
        
        String controllerSource = """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api")
            public class TestController {
                
                @GetMapping("/users/{userId}")
                public String getUser(@PathVariable(PathConstants.USER_ID) String id) {
                    return "user";
                }
                
                @GetMapping("/products/{productId}")
                public String getProduct(@PathVariable(value = PathConstants.PRODUCT_ID) String id) {
                    return "product";
                }
            }
            """;
        
        // Create directory structure
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Write the files
        Files.writeString(srcDir.resolve("PathConstants.java"), constantsSource);
        Files.writeString(srcDir.resolve("TestController.java"), controllerSource);
        
        // Scan the project
        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);
        
        // Verify the results
        assertNotNull(result);
        assertEquals(2, result.getEndpoints().size());
        
        // Check user endpoint
        ApiEndpoint userEndpoint = result.getEndpoints().stream()
            .filter(e -> e.getPath().contains("users"))
            .findFirst()
            .orElse(null);
        assertNotNull(userEndpoint);
        ApiEndpoint.Parameter userParam = userEndpoint.getParameters().stream()
            .filter(p -> "path".equals(p.getIn()))
            .findFirst()
            .orElse(null);
        assertNotNull(userParam);
        assertEquals("userId", userParam.getName());
        
        // Check product endpoint
        ApiEndpoint productEndpoint = result.getEndpoints().stream()
            .filter(e -> e.getPath().contains("products"))
            .findFirst()
            .orElse(null);
        assertNotNull(productEndpoint);
        ApiEndpoint.Parameter productParam = productEndpoint.getParameters().stream()
            .filter(p -> "path".equals(p.getIn()))
            .findFirst()
            .orElse(null);
        assertNotNull(productParam);
        assertEquals("productId", productParam.getName());
    }
}