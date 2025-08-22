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

class FileUploadParameterTest {
    
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
    void testSingleFileUpload(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.multipart.MultipartFile;
            import org.springframework.http.HttpStatus;
            
            @RestController
            @RequestMapping("/api/v1")
            public class FileController {
                
                @PostMapping("/private/file")
                @ResponseStatus(HttpStatus.CREATED)
                public void upload(@RequestParam("file") MultipartFile file) {
                    // Upload file logic
                }
            }
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/FileController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/api/v1/private/file", endpoint.getPath());
        assertEquals("POST", endpoint.getHttpMethod());
        
        // Verify file parameter is detected correctly
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(1, parameters.size());
        
        ApiEndpoint.Parameter fileParam = parameters.get(0);
        assertEquals("file", fileParam.getName());
        assertEquals("MultipartFile", fileParam.getType());
        assertEquals("formData", fileParam.getIn());
        assertTrue(fileParam.isRequired()); // @RequestParam defaults to required=true
    }
    
    @Test
    void testMultipleFileUpload(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.multipart.MultipartFile;
            import org.springframework.http.HttpStatus;
            import org.springframework.http.MediaType;
            
            @RestController
            @RequestMapping("/api/v1")
            public class FileController {
                
                @PostMapping(value = "/private/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
                @ResponseStatus(HttpStatus.CREATED)
                public void uploadMultiple(@RequestParam(value = "file[]", required = true) MultipartFile[] files) {
                    // Upload multiple files logic
                }
            }
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/FileController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/api/v1/private/files", endpoint.getPath());
        assertEquals("POST", endpoint.getHttpMethod());
        
        // Verify file array parameter is detected correctly
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(1, parameters.size());
        
        ApiEndpoint.Parameter fileParam = parameters.get(0);
        assertEquals("file[]", fileParam.getName());
        assertEquals("MultipartFile[]", fileParam.getType());
        assertEquals("formData", fileParam.getIn());
        assertTrue(fileParam.isRequired());
    }
    
    @Test
    void testMixedParametersWithFileUpload(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.web.multipart.MultipartFile;
            import org.springframework.http.HttpStatus;
            
            @RestController
            public class DocumentController {
                
                @PostMapping("/documents/{categoryId}")
                public void uploadDocument(
                        @PathVariable Long categoryId,
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "description", required = false) String description) {
                    // Upload document with metadata
                }
            }
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/DocumentController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        assertEquals("/documents/{categoryId}", endpoint.getPath());
        assertEquals("POST", endpoint.getHttpMethod());
        
        // Verify all parameters are detected with correct types
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(3, parameters.size());
        
        // Check path parameter
        Optional<ApiEndpoint.Parameter> categoryIdParam = parameters.stream()
                .filter(p -> p.getName().equals("categoryId"))
                .findFirst();
        assertTrue(categoryIdParam.isPresent());
        assertEquals("path", categoryIdParam.get().getIn());
        assertEquals("Long", categoryIdParam.get().getType());
        
        // Check file parameter
        Optional<ApiEndpoint.Parameter> fileParam = parameters.stream()
                .filter(p -> p.getName().equals("file"))
                .findFirst();
        assertTrue(fileParam.isPresent());
        assertEquals("formData", fileParam.get().getIn());
        assertEquals("MultipartFile", fileParam.get().getType());
        assertTrue(fileParam.get().isRequired());
        
        // Check regular query parameter
        Optional<ApiEndpoint.Parameter> descParam = parameters.stream()
                .filter(p -> p.getName().equals("description"))
                .findFirst();
        assertTrue(descParam.isPresent());
        assertEquals("query", descParam.get().getIn());
        assertEquals("String", descParam.get().getType());
        assertFalse(descParam.get().isRequired());
    }
    
    @Test
    void testFullyQualifiedMultipartFileType(@TempDir Path tempDir) throws IOException {
        createTestProject(tempDir);
        
        String source = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            public class ImageController {
                
                @PostMapping("/images")
                public void uploadImage(@RequestParam("image") org.springframework.web.multipart.MultipartFile image) {
                    // Upload image with fully qualified type
                }
            }
            """;
        
        Path controllerFile = tempDir.resolve("src/main/java/com/example/controller/ImageController.java");
        Files.writeString(controllerFile, source);
        
        ScanResult result = scanner.scan(tempDir);
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        assertEquals(1, endpoints.size());
        
        ApiEndpoint endpoint = endpoints.get(0);
        List<ApiEndpoint.Parameter> parameters = endpoint.getParameters();
        assertEquals(1, parameters.size());
        
        ApiEndpoint.Parameter imageParam = parameters.get(0);
        assertEquals("image", imageParam.getName());
        assertEquals("org.springframework.web.multipart.MultipartFile", imageParam.getType());
        assertEquals("formData", imageParam.getIn());
    }
}