package com.apiscan.cli;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for parent directory multi-module project DTO resolution.
 * This tests the fix for scanning projects like "shopizer/" that contain 
 * multiple modules as subdirectories (sm-shop-model, sm-core-model, etc.)
 */
public class ParentDirectoryMultiModuleTest {

    @TempDir
    Path tempDir;
    
    private DtoSchemaResolver resolver;
    private Path parentProject;
    private Path shopModelModule;
    private Path coreModelModule;

    @BeforeEach
    void setUp() throws IOException {
        // Create parent project directory structure similar to Shopizer
        parentProject = tempDir.resolve("shopizer-project");
        shopModelModule = parentProject.resolve("sm-shop-model");
        coreModelModule = parentProject.resolve("sm-core-model");
        
        Files.createDirectories(parentProject);
        Files.createDirectories(shopModelModule);
        Files.createDirectories(coreModelModule);
        
        // Create pom.xml files to indicate Maven modules
        Files.createFile(parentProject.resolve("pom.xml"));
        Files.createFile(shopModelModule.resolve("pom.xml"));
        Files.createFile(coreModelModule.resolve("pom.xml"));
        
        // Create DTOs in sub-modules
        createShopModelDto();
        createCoreModelDto();
        
        // Initialize resolver with PARENT project path (the key scenario that was broken)
        resolver = new DtoSchemaResolver(parentProject.toString());
    }
    
    private void createShopModelDto() throws IOException {
        Path dtoDir = shopModelModule.resolve("src/main/java/com/salesmanager/shop/model/customer");
        Files.createDirectories(dtoDir);
        
        String dtoContent = """
            package com.salesmanager.shop.model.customer;
            
            import java.util.List;
            
            /**
             * Customer DTO for API operations
             */
            public class PersistableCustomer {
                private String emailAddress;
                private String password;
                private String firstName;
                private String lastName;
                private List<String> groups;
                
                public String getEmailAddress() {
                    return emailAddress;
                }
                
                public void setEmailAddress(String emailAddress) {
                    this.emailAddress = emailAddress;
                }
                
                public String getPassword() {
                    return password;
                }
                
                public void setPassword(String password) {
                    this.password = password;
                }
                
                public String getFirstName() {
                    return firstName;
                }
                
                public void setFirstName(String firstName) {
                    this.firstName = firstName;
                }
                
                public String getLastName() {
                    return lastName;
                }
                
                public void setLastName(String lastName) {
                    this.lastName = lastName;
                }
                
                public List<String> getGroups() {
                    return groups;
                }
                
                public void setGroups(List<String> groups) {
                    this.groups = groups;
                }
            }
            """;
        
        Files.write(dtoDir.resolve("PersistableCustomer.java"), dtoContent.getBytes());
    }
    
    private void createCoreModelDto() throws IOException {
        Path dtoDir = coreModelModule.resolve("src/main/java/com/salesmanager/core/model/customer");
        Files.createDirectories(dtoDir);
        
        String dtoContent = """
            package com.salesmanager.core.model.customer;
            
            /**
             * Core JPA entity (should be deprioritized in favor of shop model)
             */
            public class Customer {
                private Long id;
                private String internalCode;
                
                public Long getId() {
                    return id;
                }
                
                public void setId(Long id) {
                    this.id = id;
                }
                
                public String getInternalCode() {
                    return internalCode;
                }
                
                public void setInternalCode(String internalCode) {
                    this.internalCode = internalCode;
                }
            }
            """;
        
        Files.write(dtoDir.resolve("Customer.java"), dtoContent.getBytes());
    }

    @Test
    void shouldResolveSchemaFromSubModuleWhenScanningParentDirectory() {
        // When resolving a DTO from parent project directory
        Schema<?> schema = resolver.resolveSchema("PersistableCustomer");
        
        // Then it should find and parse the schema from the sub-module
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getDescription().contains("Customer"), 
            "Description should mention Customer (either from JavaDoc or default pattern)");
        
        Map<String, Schema> properties = schema.getProperties();
        assertNotNull(properties);
        assertTrue(properties.containsKey("emailAddress"));
        assertTrue(properties.containsKey("password"));
        assertTrue(properties.containsKey("firstName"));
        assertTrue(properties.containsKey("lastName"));
        assertTrue(properties.containsKey("groups"));
        
        // Verify property types
        assertEquals("string", properties.get("emailAddress").getType());
        assertEquals("string", properties.get("password").getType());
        assertEquals("string", properties.get("firstName").getType());
        assertEquals("string", properties.get("lastName").getType());
        assertEquals("array", properties.get("groups").getType());
    }
    
    @Test
    void shouldNotReturnPlaceholderSchemaWhenDtoExistsInSubModule() {
        // When resolving a DTO that exists in a sub-module
        Schema<?> schema = resolver.resolveSchema("PersistableCustomer");
        
        // Then it should NOT be a placeholder schema
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        assertFalse(schema.getProperties().containsKey("_schemaPlaceholder"), 
            "Should not contain placeholder property when DTO is found in sub-module");
        
        String description = schema.getDescription();
        assertNotNull(description);
        assertFalse(description.contains("Schema not available"), 
            "Description should not indicate schema unavailability");
        assertFalse(description.contains("generated source may be missing"), 
            "Description should not mention missing generated source");
    }
    
    @Test
    void shouldPrioritizeShopModelOverCoreModelInSubModules() {
        // This test verifies that when multiple modules contain similar classes,
        // the prioritization logic still works when scanning from parent directory
        
        // When there are multiple potential DTO classes across sub-modules
        // (This would require the resolver to find both Customer and PersistableCustomer)
        Schema<?> persistableCustomerSchema = resolver.resolveSchema("PersistableCustomer");
        
        // Then it should successfully resolve from the appropriate module
        assertNotNull(persistableCustomerSchema);
        assertTrue(persistableCustomerSchema.getProperties().containsKey("emailAddress"), 
            "Should find PersistableCustomer from sm-shop-model with DTO-appropriate fields");
        assertTrue(persistableCustomerSchema.getProperties().containsKey("password"), 
            "Should have DTO fields like password, not internal JPA fields");
    }
    
    @Test
    void shouldWorkWithBothCurrentModuleAndSubModuleSearch() {
        // Test the complete search strategy that includes both:
        // 1. Current module search (should find nothing in parent project)
        // 2. Sub-module search (should find PersistableCustomer in sm-shop-model)
        
        Schema<?> schema = resolver.resolveSchema("PersistableCustomer");
        
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().size() >= 5, 
            "Should have resolved actual properties from sub-module, not just placeholder");
    }
    
    @Test
    void shouldHandleEmptyParentDirectoryGracefully() {
        // Create a resolver with parent project that has no sub-modules with DTOs
        Path emptyProject = tempDir.resolve("empty-project");
        try {
            Files.createDirectories(emptyProject);
            Files.createFile(emptyProject.resolve("pom.xml"));
        } catch (IOException e) {
            fail("Setup failed: " + e.getMessage());
        }
        
        DtoSchemaResolver emptyResolver = new DtoSchemaResolver(emptyProject.toString());
        
        // When resolving a non-existent DTO
        Schema<?> schema = emptyResolver.resolveSchema("NonExistentDto");
        
        // Then it should return a placeholder schema gracefully
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getDescription().contains("Schema not available"));
        assertTrue(schema.getProperties().containsKey("_schemaPlaceholder"));
    }
}