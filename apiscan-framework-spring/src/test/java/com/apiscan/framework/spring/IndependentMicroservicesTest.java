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

class IndependentMicroservicesTest {
    
    private SpringFrameworkScanner scanner;
    private SpringFrameworkDetector detector;
    
    @BeforeEach
    void setUp() {
        scanner = new SpringFrameworkScanner();
        detector = new SpringFrameworkDetector();
    }
    
    @Test
    void testIndependentMicroservicesDetection(@TempDir Path tempDir) throws IOException {
        // Create structure similar to Spring-Boot-Microservices-Banking-Application
        createIndependentMicroservicesProject(tempDir);
        
        ScanResult result = scanner.scan(tempDir);
        
        // Should detect endpoints from multiple microservices
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertTrue(endpoints.size() >= 3, "Should detect endpoints from multiple microservices");
        
        // Verify endpoints from different services are detected
        boolean hasAccountService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("AccountController"));
        boolean hasUserService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("UserController"));
        boolean hasTransactionService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("TransactionController"));
        
        assertTrue(hasAccountService, "Should detect Account Service endpoints");
        assertTrue(hasUserService, "Should detect User Service endpoints");
        assertTrue(hasTransactionService, "Should detect Transaction Service endpoints");
    }
    
    @Test
    void testMinimumMicroservicesThreshold(@TempDir Path tempDir) throws IOException {
        // Create only one microservice - should not be detected as microservices project
        createSingleMicroservice(tempDir, "account-service");
        
        ScanResult result = scanner.scan(tempDir);
        
        // Should be treated as single module, not microservices
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(0, endpoints.size(), "Single service should not trigger microservices detection");
    }
    
    @Test
    void testMixedMavenAndGradleMicroservices(@TempDir Path tempDir) throws IOException {
        // Create microservices with different build tools
        createMavenMicroservice(tempDir, "account-service");
        createGradleMicroservice(tempDir, "user-service");
        createGradleKotlinMicroservice(tempDir, "notification-service");
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertTrue(endpoints.size() >= 3, "Should detect endpoints from Maven and Gradle microservices");
        
        // Verify all services are detected
        boolean hasAccountService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("AccountController"));
        boolean hasUserService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("UserController"));
        boolean hasNotificationService = endpoints.stream()
            .anyMatch(ep -> ep.getControllerClass().contains("NotificationController"));
        
        assertTrue(hasAccountService, "Should detect Maven-based Account Service");
        assertTrue(hasUserService, "Should detect Gradle-based User Service");
        assertTrue(hasNotificationService, "Should detect Gradle Kotlin-based Notification Service");
    }
    
    @Test
    void testExcludeNonServiceDirectories(@TempDir Path tempDir) throws IOException {
        // Create microservices along with non-service directories
        createMavenMicroservice(tempDir, "account-service");
        createMavenMicroservice(tempDir, "user-service");
        
        // Create directories without build files (should be ignored)
        Files.createDirectories(tempDir.resolve("docs"));
        Files.createDirectories(tempDir.resolve("scripts"));
        Files.createDirectories(tempDir.resolve("config"));
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(2, endpoints.size(), "Should only scan directories with build files");
    }
    
    @Test
    void testSpringFrameworkDetectorRecognizesMicroservices(@TempDir Path tempDir) throws IOException {
        // Create microservices structure without parent pom.xml
        createMavenMicroservice(tempDir, "account-service");
        createMavenMicroservice(tempDir, "user-service");
        
        // Detector should recognize this as a Spring project
        assertTrue(detector.detect(tempDir), "Should detect Spring microservices");
        assertEquals("Spring", detector.getFrameworkName());
    }
    
    @Test
    void testSpringFrameworkDetectorIgnoresSingleService(@TempDir Path tempDir) throws IOException {
        // Create only one service - should not trigger microservices detection in detector
        createMavenMicroservice(tempDir, "account-service");
        
        // Should not detect as microservices (needs 2+ services)
        assertFalse(detector.detect(tempDir), "Single service should not trigger microservices detection");
    }
    
    @Test
    void testWithParentBuildFileShouldNotTriggerMicroservicesDetection(@TempDir Path tempDir) throws IOException {
        // Create parent pom.xml (should trigger multi-module detection instead)
        Files.writeString(tempDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>parent</artifactId>
                <version>1.0.0</version>
                <packaging>pom</packaging>
            </project>
            """);
        
        createMavenMicroservice(tempDir, "account-service");
        createMavenMicroservice(tempDir, "user-service");
        
        ScanResult result = scanner.scan(tempDir);
        
        // Should be treated as multi-module project, not independent microservices
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(2, endpoints.size(), "Should still detect endpoints but via multi-module path");
    }
    
    private void createIndependentMicroservicesProject(Path parentDir) throws IOException {
        createMavenMicroservice(parentDir, "Account-Service");
        createMavenMicroservice(parentDir, "User-Service");
        createMavenMicroservice(parentDir, "Transaction-Service");
    }
    
    private void createSingleMicroservice(Path parentDir, String serviceName) throws IOException {
        createMavenMicroservice(parentDir, serviceName);
    }
    
    private void createMavenMicroservice(Path parentDir, String serviceName) throws IOException {
        Path serviceDir = parentDir.resolve(serviceName);
        Path srcDir = serviceDir.resolve("src/main/java/com/example/" + serviceName.toLowerCase().replace("-", ""));
        Files.createDirectories(srcDir);
        
        // Create pom.xml
        Files.writeString(serviceDir.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>%s</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>2.7.0</version>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(serviceName));
        
        // Create controller
        String baseName = serviceName.replace("-", "").toLowerCase().replace("service", "");
        String controllerName = capitalize(baseName) + "Controller";
        Files.writeString(srcDir.resolve(controllerName + ".java"), """
            package com.example.%s;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1/%s")
            public class %s {
                
                @GetMapping
                public String getAll() {
                    return "list";
                }
            }
            """.formatted(
                serviceName.toLowerCase().replace("-", ""),
                serviceName.toLowerCase().replace("-service", ""),
                controllerName
            ));
    }
    
    private void createGradleMicroservice(Path parentDir, String serviceName) throws IOException {
        Path serviceDir = parentDir.resolve(serviceName);
        Path srcDir = serviceDir.resolve("src/main/java/com/example/" + serviceName.toLowerCase().replace("-", ""));
        Files.createDirectories(srcDir);
        
        // Create build.gradle
        Files.writeString(serviceDir.resolve("build.gradle"), """
            plugins {
                id 'java'
                id 'org.springframework.boot' version '3.0.0'
            }
            
            group = 'com.example'
            version = '1.0.0'
            
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web'
            }
            """);
        
        // Create controller
        String baseName = serviceName.replace("-", "").toLowerCase().replace("service", "");
        String controllerName = capitalize(baseName) + "Controller";
        Files.writeString(srcDir.resolve(controllerName + ".java"), """
            package com.example.%s;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1/%s")
            public class %s {
                
                @PostMapping
                public String create() {
                    return "created";
                }
            }
            """.formatted(
                serviceName.toLowerCase().replace("-", ""),
                serviceName.toLowerCase().replace("-service", ""),
                controllerName
            ));
    }
    
    private void createGradleKotlinMicroservice(Path parentDir, String serviceName) throws IOException {
        Path serviceDir = parentDir.resolve(serviceName);
        Path srcDir = serviceDir.resolve("src/main/java/com/example/" + serviceName.toLowerCase().replace("-", ""));
        Files.createDirectories(srcDir);
        
        // Create build.gradle.kts
        Files.writeString(serviceDir.resolve("build.gradle.kts"), """
            plugins {
                java
                id("org.springframework.boot") version "3.0.0"
            }
            
            group = "com.example"
            version = "1.0.0"
            
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-web")
            }
            """);
        
        // Create controller
        String baseName = serviceName.replace("-", "").toLowerCase().replace("service", "");
        String controllerName = capitalize(baseName) + "Controller";
        Files.writeString(srcDir.resolve(controllerName + ".java"), """
            package com.example.%s;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/v1/%s")
            public class %s {
                
                @PutMapping("/{id}")
                public String update(@PathVariable Long id) {
                    return "updated";
                }
            }
            """.formatted(
                serviceName.toLowerCase().replace("-", ""),
                serviceName.toLowerCase().replace("-service", ""),
                controllerName
            ));
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}