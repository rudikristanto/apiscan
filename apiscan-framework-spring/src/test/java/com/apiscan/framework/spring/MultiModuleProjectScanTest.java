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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for multi-module Maven project scanning functionality.
 */
public class MultiModuleProjectScanTest {

    @TempDir
    Path tempDir;

    private SpringFrameworkScanner scanner;
    private Path parentProject;
    private Path apiModule;
    private Path modelModule;

    @BeforeEach
    void setUp() throws IOException {
        scanner = new SpringFrameworkScanner();
        
        // Create multi-module project structure
        parentProject = tempDir.resolve("multi-module-parent");
        apiModule = parentProject.resolve("api-module");
        modelModule = parentProject.resolve("model-module");
        
        Files.createDirectories(parentProject);
        Files.createDirectories(apiModule);
        Files.createDirectories(modelModule);
        
        // Create parent pom.xml
        Files.createFile(parentProject.resolve("pom.xml"));
        
        // Create module pom.xml files
        Files.createFile(apiModule.resolve("pom.xml"));
        Files.createFile(modelModule.resolve("pom.xml"));
        
        // Create module source directories
        Files.createDirectories(apiModule.resolve("src/main/java/com/example/api"));
        Files.createDirectories(modelModule.resolve("src/main/java/com/example/model"));
    }

    @Test
    void shouldDetectMultiModuleProject() throws IOException {
        // Create a controller in api-module
        createApiController();
        
        // Scan from parent directory
        ScanResult result = scanner.scan(parentProject);
        
        // Should find endpoints from all modules
        assertNotNull(result);
        assertTrue(result.getEndpoints().size() > 0);
        assertEquals("Spring", result.getFramework());
        assertTrue(result.getProjectPath().contains("multi-module-parent"));
    }

    @Test
    void shouldScanAllModulesInParentDirectory() throws IOException {
        // Create controllers in different modules
        createApiController();
        createModelController();
        
        ScanResult result = scanner.scan(parentProject);
        
        assertNotNull(result);
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Should find endpoints from both modules
        assertTrue(endpoints.size() >= 2, "Should find endpoints from multiple modules");
        
        // Verify endpoints from different modules are included
        boolean hasApiModuleEndpoint = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("ApiController"));
        boolean hasModelModuleEndpoint = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("ModelController"));
            
        assertTrue(hasApiModuleEndpoint, "Should find endpoint from api-module");
        assertTrue(hasModelModuleEndpoint, "Should find endpoint from model-module");
    }

    @Test
    void shouldHandleMixedModuleStructure() throws IOException {
        // Create one module with controllers, one without
        createApiController();
        
        // Create empty module (no controllers)
        Path emptyModule = parentProject.resolve("empty-module");
        Files.createDirectories(emptyModule);
        Files.createFile(emptyModule.resolve("pom.xml"));
        Files.createDirectories(emptyModule.resolve("src/main/java/com/example/empty"));
        
        ScanResult result = scanner.scan(parentProject);
        
        assertNotNull(result);
        assertTrue(result.getEndpoints().size() >= 1);
        // Should not fail even with modules that have no controllers
    }

    @Test
    void shouldFallBackToSingleModuleIfNoMultiModuleDetected() throws IOException {
        // Create a single module project (no parent pom or sub-modules)
        Path singleModule = tempDir.resolve("single-module");
        Files.createDirectories(singleModule);
        Files.createDirectories(singleModule.resolve("src/main/java/com/example"));
        
        // Create controller in single module
        String controllerContent = """
            package com.example;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/single")
            public class SingleController {
                
                @GetMapping("/test")
                public String getTest() {
                    return "test";
                }
            }
            """;
        Files.write(singleModule.resolve("src/main/java/com/example/SingleController.java"), 
                   controllerContent.getBytes());
        
        ScanResult result = scanner.scan(singleModule);
        
        assertNotNull(result);
        assertTrue(result.getEndpoints().size() >= 1);
        assertTrue(result.getEndpoints().get(0).getPath().contains("/api/single/test"));
    }

    @Test
    void shouldIgnoreNonMavenDirectories() throws IOException {
        // Create regular directories without pom.xml
        Path nonMavenDir = parentProject.resolve("non-maven-dir");
        Files.createDirectories(nonMavenDir);
        Files.createDirectories(nonMavenDir.resolve("src/main/java"));
        
        // Create Maven module
        createApiController();
        
        ScanResult result = scanner.scan(parentProject);
        
        assertNotNull(result);
        // Should only scan Maven modules, not regular directories
        assertTrue(result.getFilesScanned() > 0);
    }

    private void createApiController() throws IOException {
        String controllerContent = """
            package com.example.api;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1")
            public class ApiController {
                
                @GetMapping("/users")
                public String listUsers() {
                    return "users";
                }
                
                @PostMapping("/users")
                public String createUser(@RequestBody String userData) {
                    return "created";
                }
            }
            """;
        Files.write(apiModule.resolve("src/main/java/com/example/api/ApiController.java"), 
                   controllerContent.getBytes());
    }

    private void createModelController() throws IOException {
        String controllerContent = """
            package com.example.model;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/models")
            public class ModelController {
                
                @GetMapping("/categories")
                public String listCategories() {
                    return "categories";
                }
            }
            """;
        Files.write(modelModule.resolve("src/main/java/com/example/model/ModelController.java"), 
                   controllerContent.getBytes());
    }
}