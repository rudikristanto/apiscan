package com.apiscan.framework.spring;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QueryParameterExtractionTest {
    
    private SpringFrameworkScanner scanner;
    
    @BeforeEach
    void setUp() {
        scanner = new SpringFrameworkScanner();
    }
    
    private void createTestProject(Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/main/java/com/example/controller");
        Files.createDirectories(srcDir);
    }
    
    @Test
    void testQueryParameterExtraction(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1")
            public class OrderController {
                
                @GetMapping("/orders/customers/{id}")
                public OrderList getCustomerOrders(
                        @PathVariable Long id,
                        @RequestParam(value = "start", required = false) Integer start,
                        @RequestParam(value = "count", required = false) Integer count) {
                    return new OrderList();
                }
            }
            
            class OrderList {}
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/OrderController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/api/v1/orders/customers/{id}", endpoint.getPath());
        assertEquals("GET", endpoint.getHttpMethod());
        
        // Verify all parameters are extracted
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(3, parameters.size(), "Should have 3 parameters: id, start, count");
        
        // Check path parameter
        Optional<ApiEndpoint.Parameter> idParam = parameters.stream()
                .filter(p -> p.getName().equals("id"))
                .findFirst();
        assertTrue(idParam.isPresent());
        assertEquals("path", idParam.get().getIn());
        assertEquals("Long", idParam.get().getType());
        assertTrue(idParam.get().isRequired());
        
        // Check query parameter 'start'
        Optional<ApiEndpoint.Parameter> startParam = parameters.stream()
                .filter(p -> p.getName().equals("start"))
                .findFirst();
        assertTrue(startParam.isPresent());
        assertEquals("query", startParam.get().getIn());
        assertEquals("Integer", startParam.get().getType());
        assertFalse(startParam.get().isRequired());
        
        // Check query parameter 'count'
        Optional<ApiEndpoint.Parameter> countParam = parameters.stream()
                .filter(p -> p.getName().equals("count"))
                .findFirst();
        assertTrue(countParam.isPresent());
        assertEquals("query", countParam.get().getIn());
        assertEquals("Integer", countParam.get().getType());
        assertFalse(countParam.get().isRequired());
    }
    
    @Test
    void testMixedParametersWithRequestBody(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1")
            public class ProductController {
                
                @PostMapping("/products/{id}/reviews")
                public Review createReview(
                        @PathVariable Long id,
                        @RequestParam(value = "notify", required = true) Boolean notify,
                        @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
                        @RequestBody Review review) {
                    return review;
                }
            }
            
            class Review {}
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/ProductController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/api/v1/products/{id}/reviews", endpoint.getPath());
        assertEquals("POST", endpoint.getHttpMethod());
        
        // Verify all parameters are extracted
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(3, parameters.size(), "Should have 3 parameters: id, notify, lang (RequestBody is separate)");
        
        // Check path parameter
        Optional<ApiEndpoint.Parameter> idParam = parameters.stream()
                .filter(p -> p.getName().equals("id"))
                .findFirst();
        assertTrue(idParam.isPresent());
        assertEquals("path", idParam.get().getIn());
        
        // Check required query parameter
        Optional<ApiEndpoint.Parameter> notifyParam = parameters.stream()
                .filter(p -> p.getName().equals("notify"))
                .findFirst();
        assertTrue(notifyParam.isPresent());
        assertEquals("query", notifyParam.get().getIn());
        assertEquals("Boolean", notifyParam.get().getType());
        assertTrue(notifyParam.get().isRequired());
        
        // Check optional query parameter with default
        Optional<ApiEndpoint.Parameter> langParam = parameters.stream()
                .filter(p -> p.getName().equals("lang"))
                .findFirst();
        assertTrue(langParam.isPresent());
        assertEquals("query", langParam.get().getIn());
        assertEquals("String", langParam.get().getType());
        assertFalse(langParam.get().isRequired());
        
        // Check request body is detected
        assertNotNull(endpoint.getRequestBody());
    }
    
    @Test
    void testQueryParametersWithoutValue(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            public class SearchController {
                
                @GetMapping("/search")
                public SearchResult search(
                        @RequestParam String q,
                        @RequestParam(required = false) String category,
                        @RequestParam(defaultValue = "10") Integer limit) {
                    return new SearchResult();
                }
            }
            
            class SearchResult {}
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/SearchController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/search", endpoint.getPath());
        
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(3, parameters.size());
        
        // Check 'q' parameter (without explicit value or required attribute)
        Optional<ApiEndpoint.Parameter> qParam = parameters.stream()
                .filter(p -> p.getName().equals("q"))
                .findFirst();
        assertTrue(qParam.isPresent());
        assertEquals("query", qParam.get().getIn());
        assertTrue(qParam.get().isRequired()); // Default is required=true
        
        // Check 'category' parameter
        Optional<ApiEndpoint.Parameter> categoryParam = parameters.stream()
                .filter(p -> p.getName().equals("category"))
                .findFirst();
        assertTrue(categoryParam.isPresent());
        assertEquals("query", categoryParam.get().getIn());
        assertFalse(categoryParam.get().isRequired());
        
        // Check 'limit' parameter
        Optional<ApiEndpoint.Parameter> limitParam = parameters.stream()
                .filter(p -> p.getName().equals("limit"))
                .findFirst();
        assertTrue(limitParam.isPresent());
        assertEquals("query", limitParam.get().getIn());
        assertEquals("Integer", limitParam.get().getType());
    }
}