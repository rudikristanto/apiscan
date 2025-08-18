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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SpringFrameworkScannerTest {
    
    private SpringFrameworkScanner scanner;
    
    @BeforeEach
    void setUp() {
        scanner = new SpringFrameworkScanner();
    }
    
    @Test
    void testDirectAnnotationBasedAPI(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "direct-annotation");
        
        // Create controller with direct annotations
        String controllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            import java.util.List;
            
            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                
                @GetMapping
                public ResponseEntity<List<User>> listUsers() {
                    return null;
                }
                
                @GetMapping("/{id}")
                public ResponseEntity<User> getUser(@PathVariable Long id) {
                    return null;
                }
                
                @PostMapping
                public ResponseEntity<User> createUser(@RequestBody UserDto userDto) {
                    return null;
                }
                
                @PutMapping("/{id}")
                public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody UserDto userDto) {
                    return null;
                }
                
                @DeleteMapping("/{id}")
                public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/UserController.java", controllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results
        assertNotNull(result);
        assertEquals(5, result.getEndpoints().size());
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Verify GET /api/users
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().equals("/api/users") && e.getMethodName().equals("listUsers")));
        
        // Verify GET /api/users/{id}
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().equals("/api/users/{id}") && e.getMethodName().equals("getUser")));
        
        // Verify POST /api/users
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("POST") && e.getPath().equals("/api/users") && e.getMethodName().equals("createUser")));
        
        // Verify PUT /api/users/{id}
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("PUT") && e.getPath().equals("/api/users/{id}") && e.getMethodName().equals("updateUser")));
        
        // Verify DELETE /api/users/{id}
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("DELETE") && e.getPath().equals("/api/users/{id}") && e.getMethodName().equals("deleteUser")));
    }
    
    @Test
    void testInterfaceBasedAPIWithDefinitions(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "interface-with-definitions");
        
        // Create API interface with annotations
        String interfaceContent = """
            package com.example.api;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            import java.util.List;
            
            @RequestMapping("/api/products")
            public interface ProductsApi {
                
                @GetMapping
                ResponseEntity<List<Product>> listProducts(@RequestParam(required = false) String category);
                
                @GetMapping("/{productId}")
                ResponseEntity<Product> getProduct(@PathVariable Integer productId);
                
                @PostMapping
                ResponseEntity<Product> addProduct(@RequestBody ProductDto productDto);
                
                @PutMapping("/{productId}")
                ResponseEntity<Product> updateProduct(@PathVariable Integer productId, @RequestBody ProductDto productDto);
                
                @DeleteMapping("/{productId}")
                ResponseEntity<Void> deleteProduct(@PathVariable Integer productId);
            }
            """;
        
        // Create controller implementing the interface
        String controllerContent = """
            package com.example.controller;
            
            import com.example.api.ProductsApi;
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.http.ResponseEntity;
            import java.util.List;
            
            @RestController
            public class ProductController implements ProductsApi {
                
                @Override
                public ResponseEntity<List<Product>> listProducts(String category) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Product> getProduct(Integer productId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Product> addProduct(ProductDto productDto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Product> updateProduct(Integer productId, ProductDto productDto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Void> deleteProduct(Integer productId) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/api/ProductsApi.java", interfaceContent);
        writeJavaFile(tempDir, "com/example/controller/ProductController.java", controllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results
        assertNotNull(result);
        assertEquals(10, result.getEndpoints().size()); // 5 from interface + 5 from interface scanning
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Verify endpoints are detected
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().equals("/api/products")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().equals("/api/products/{productId}")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("POST") && e.getPath().equals("/api/products")));
    }
    
    @Test
    void testInterfaceBasedAPIWithMissingDefinitions(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "interface-missing-definitions");
        
        // Create controller implementing external interface (definition not available)
        String controllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            import java.util.List;
            
            @RestController
            @RequestMapping("/api/orders")
            public class OrderController implements OrdersApi {
                
                @Override
                public ResponseEntity<List<Order>> listOrders(String status) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Order> getOrder(Integer orderId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Order> addOrder(OrderDto orderDto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Order> updateOrder(Integer orderId, OrderDto orderDto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Void> deleteOrder(Integer orderId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<OrderItem> addOrderItem(Integer orderId, OrderItemDto itemDto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<List<OrderItem>> getOrderItems(Integer orderId) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/OrderController.java", controllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results - should infer endpoints from @Override methods
        assertNotNull(result);
        assertEquals(7, result.getEndpoints().size());
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Verify inferred endpoints (flexible path matching)
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().contains("order") && e.getMethodName().equals("listOrders")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().contains("order") && e.getMethodName().equals("getOrder")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("POST") && e.getPath().contains("order") && e.getMethodName().equals("addOrder")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("PUT") && e.getPath().contains("order") && e.getMethodName().equals("updateOrder")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("DELETE") && e.getPath().contains("order") && e.getMethodName().equals("deleteOrder")));
        
        // Verify nested resource endpoints (flexible matching)
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("POST") && e.getPath().contains("order") && e.getMethodName().equals("addOrderItem")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().contains("order") && e.getMethodName().equals("getOrderItems")));
    }
    
    @Test
    void testMixedScenarios(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "mixed-scenarios");
        
        // Direct annotation controller
        String directControllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            
            @RestController
            @RequestMapping("/api/categories")
            public class CategoryController {
                
                @GetMapping
                public ResponseEntity<List<Category>> listCategories() {
                    return null;
                }
                
                @PostMapping
                public ResponseEntity<Category> createCategory(@RequestBody CategoryDto dto) {
                    return null;
                }
            }
            """;
        
        // Interface-based controller (missing interface definition)
        String interfaceControllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            
            @RestController
            @RequestMapping("/api/tags")
            public class TagController implements TagsApi {
                
                @Override
                public ResponseEntity<List<Tag>> listTags() {
                    return null;
                }
                
                @Override
                public ResponseEntity<Tag> getTag(Integer tagId) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/CategoryController.java", directControllerContent);
        writeJavaFile(tempDir, "com/example/controller/TagController.java", interfaceControllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results from both controllers
        assertNotNull(result);
        assertEquals(4, result.getEndpoints().size());
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Direct annotation endpoints
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().equals("/api/categories") && e.getControllerClass().equals("CategoryController")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("POST") && e.getPath().equals("/api/categories") && e.getControllerClass().equals("CategoryController")));
        
        // Inferred interface endpoints (flexible matching)
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().contains("tag") && e.getControllerClass().equals("TagController")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getHttpMethod().equals("GET") && e.getPath().contains("tag") && e.getControllerClass().equals("TagController")));
    }
    
    @Test
    void testComplexNestedResources(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "complex-nested");
        
        String controllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            
            @RestController
            @RequestMapping("/api/companies")
            public class CompanyController implements CompaniesApi {
                
                @Override
                public ResponseEntity<List<Company>> listCompanies() {
                    return null;
                }
                
                @Override
                public ResponseEntity<Company> getCompany(Integer companyId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Department> addDepartmentToCompany(Integer companyId, DepartmentDto dto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<List<Department>> getCompanyDepartments(Integer companyId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Employee> addEmployeeToDepartment(Integer companyId, Integer departmentId, EmployeeDto dto) {
                    return null;
                }
                
                @Override
                public ResponseEntity<List<Employee>> getDepartmentEmployees(Integer companyId, Integer departmentId) {
                    return null;
                }
                
                @Override
                public ResponseEntity<Project> addProjectToEmployee(Integer companyId, Integer departmentId, Integer employeeId, ProjectDto dto) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/CompanyController.java", controllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results
        assertNotNull(result);
        assertEquals(7, result.getEndpoints().size());
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Verify complex nested paths are inferred (flexible matching)
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getPath().contains("companies") && e.getMethodName().equals("addDepartmentToCompany")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getPath().contains("companies") && e.getMethodName().equals("getCompanyDepartments")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getPath().contains("companies") && e.getMethodName().equals("addEmployeeToDepartment")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getPath().contains("companies") && e.getMethodName().equals("getDepartmentEmployees")));
        assertTrue(endpoints.stream().anyMatch(e -> 
            e.getPath().contains("projects") || e.getPath().contains("companies") && e.getMethodName().equals("addProjectToEmployee")));
    }
    
    @Test
    void testDifferentHTTPMethodAnnotations(@TempDir Path tempDir) throws IOException {
        // Create test project structure
        createTestProject(tempDir, "http-methods");
        
        String controllerContent = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            
            @RestController
            @RequestMapping("/api/resources")
            public class ResourceController {
                
                @RequestMapping(method = RequestMethod.GET)
                public ResponseEntity<List<Resource>> listResources() {
                    return null;
                }
                
                @RequestMapping(value = "/{id}", method = RequestMethod.GET)
                public ResponseEntity<Resource> getResource(@PathVariable Integer id) {
                    return null;
                }
                
                @RequestMapping(method = RequestMethod.POST)
                public ResponseEntity<Resource> createResource(@RequestBody ResourceDto dto) {
                    return null;
                }
                
                @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
                public ResponseEntity<Resource> updateResource(@PathVariable Integer id, @RequestBody ResourceDto dto) {
                    return null;
                }
                
                @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
                public ResponseEntity<Void> deleteResource(@PathVariable Integer id) {
                    return null;
                }
                
                @PatchMapping("/{id}")
                public ResponseEntity<Resource> patchResource(@PathVariable Integer id, @RequestBody ResourcePatchDto dto) {
                    return null;
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/ResourceController.java", controllerContent);
        
        // Scan the project
        ScanResult result = scanner.scan(tempDir);
        
        // Verify results
        assertNotNull(result);
        assertEquals(6, result.getEndpoints().size());
        
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Verify all HTTP methods are detected correctly
        assertTrue(endpoints.stream().anyMatch(e -> e.getHttpMethod().equals("GET")));
        assertTrue(endpoints.stream().anyMatch(e -> e.getHttpMethod().equals("POST")));
        assertTrue(endpoints.stream().anyMatch(e -> e.getHttpMethod().equals("PUT")));
        assertTrue(endpoints.stream().anyMatch(e -> e.getHttpMethod().equals("DELETE")));
        assertTrue(endpoints.stream().anyMatch(e -> e.getHttpMethod().equals("PATCH")));
        
        // Count methods
        assertEquals(2, endpoints.stream().mapToInt(e -> e.getHttpMethod().equals("GET") ? 1 : 0).sum());
        assertEquals(1, endpoints.stream().mapToInt(e -> e.getHttpMethod().equals("POST") ? 1 : 0).sum());
        assertEquals(1, endpoints.stream().mapToInt(e -> e.getHttpMethod().equals("PUT") ? 1 : 0).sum());
        assertEquals(1, endpoints.stream().mapToInt(e -> e.getHttpMethod().equals("DELETE") ? 1 : 0).sum());
        assertEquals(1, endpoints.stream().mapToInt(e -> e.getHttpMethod().equals("PATCH") ? 1 : 0).sum());
    }
    
    @Test
    public void testRequestBodyDetectionInInferredEndpoints() throws IOException {
        // Test that request body is properly detected for DTO parameters in inferred endpoints
        Path tempDir = Files.createTempDirectory("test-request-body");
        createTestProject(tempDir, "test-project");
        
        // Create controller implementing interface with DTO parameters
        String ownerController = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            import org.springframework.http.ResponseEntity;
            import com.example.dto.OwnerFieldsDto;
            import com.example.dto.OwnerDto;
            
            @RestController
            @RequestMapping("/api/owners")
            public class OwnerController implements OwnersApi {
                
                @Override
                public ResponseEntity<OwnerDto> addOwner(OwnerFieldsDto ownerFields) {
                    // Implementation
                    return ResponseEntity.ok(new OwnerDto());
                }
                
                @Override
                public ResponseEntity<OwnerDto> updateOwner(Integer ownerId, OwnerFieldsDto ownerFields) {
                    // Implementation  
                    return ResponseEntity.ok(new OwnerDto());
                }
                
                @Override
                public ResponseEntity<OwnerDto> getOwner(Integer ownerId) {
                    // Implementation
                    return ResponseEntity.ok(new OwnerDto());
                }
            }
            """;
        
        writeJavaFile(tempDir, "com/example/controller/OwnerController.java", ownerController);
        
        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);
        
        assertNotNull(result);
        List<ApiEndpoint> endpoints = result.getEndpoints();
        
        // Find POST endpoint
        Optional<ApiEndpoint> postEndpoint = endpoints.stream()
            .filter(e -> e.getHttpMethod().equals("POST") && e.getPath().equals("/api/owners/owners"))
            .findFirst();
        
        assertTrue(postEndpoint.isPresent(), "POST /api/owners/owners endpoint should exist");
        assertNotNull(postEndpoint.get().getRequestBody(), "POST endpoint should have request body");
        assertTrue(postEndpoint.get().getRequestBody().getContent().containsKey("application/json"));
        assertEquals("OwnerFieldsDto", 
            postEndpoint.get().getRequestBody().getContent().get("application/json").getSchema());
        
        // Find PUT endpoint  
        Optional<ApiEndpoint> putEndpoint = endpoints.stream()
            .filter(e -> e.getHttpMethod().equals("PUT") && e.getPath().contains("{"))
            .findFirst();
        
        assertTrue(putEndpoint.isPresent(), "PUT endpoint should exist");
        assertNotNull(putEndpoint.get().getRequestBody(), "PUT endpoint should have request body");
        assertEquals("OwnerFieldsDto",
            putEndpoint.get().getRequestBody().getContent().get("application/json").getSchema());
        
        // Verify GET endpoint has no request body
        Optional<ApiEndpoint> getEndpoint = endpoints.stream()
            .filter(e -> e.getHttpMethod().equals("GET") && e.getPath().contains("{"))
            .findFirst();
        
        assertTrue(getEndpoint.isPresent(), "GET endpoint should exist");
        assertNull(getEndpoint.get().getRequestBody(), "GET endpoint should not have request body");
    }
    
    private void createTestProject(Path tempDir, String projectName) throws IOException {
        // Create Maven project structure
        Path srcDir = tempDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        
        // Create pom.xml with Spring dependencies
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <packaging>jar</packaging>
                
                <dependencies>
                    <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-web</artifactId>
                        <version>5.3.21</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>2.7.1</version>
                    </dependency>
                </dependencies>
            </project>
            """;
        
        Files.writeString(tempDir.resolve("pom.xml"), pomContent);
    }
    
    private void writeJavaFile(Path baseDir, String relativePath, String content) throws IOException {
        Path filePath = baseDir.resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
    }
}