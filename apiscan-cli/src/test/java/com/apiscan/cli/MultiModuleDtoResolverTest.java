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
 * Tests for multi-module Maven project DTO resolution.
 */
public class MultiModuleDtoResolverTest {

    @TempDir
    Path tempDir;
    
    private DtoSchemaResolver resolver;
    private Path mainModule;
    private Path modelModule;

    @BeforeEach
    void setUp() throws IOException {
        // Create multi-module project structure
        mainModule = tempDir.resolve("main-module");
        modelModule = tempDir.resolve("model-module");
        
        Files.createDirectories(mainModule);
        Files.createDirectories(modelModule);
        
        // Create pom.xml files to indicate Maven modules
        Files.createFile(mainModule.resolve("pom.xml"));
        Files.createFile(modelModule.resolve("pom.xml"));
        
        // Create the DTO in the model module
        createTestDto();
        
        resolver = new DtoSchemaResolver(mainModule.toString());
    }
    
    private void createTestDto() throws IOException {
        Path dtoDir = modelModule.resolve("src/main/java/com/example");
        Files.createDirectories(dtoDir);
        
        String dtoContent = """
            package com.example;
            
            /**
             * Test DTO for multi-module resolution.
             */
            public class TestCustomer {
                private String firstName;
                private String lastName;
                private int age;
                
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
                
                public int getAge() {
                    return age;
                }
                
                public void setAge(int age) {
                    this.age = age;
                }
            }
            """;
        
        Files.write(dtoDir.resolve("TestCustomer.java"), dtoContent.getBytes());
    }

    @Test
    void shouldResolveSchemaFromSiblingModule() {
        // When resolving a DTO that exists in a sibling module
        Schema<?> schema = resolver.resolveSchema("TestCustomer");
        
        // Then it should find and parse the schema
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertEquals("Test DTO for multi-module resolution.", schema.getDescription());
        
        Map<String, Schema> properties = schema.getProperties();
        assertNotNull(properties);
        assertTrue(properties.containsKey("firstName"));
        assertTrue(properties.containsKey("lastName"));
        assertTrue(properties.containsKey("age"));
        
        assertEquals("string", properties.get("firstName").getType());
        assertEquals("string", properties.get("lastName").getType());
        assertEquals("integer", properties.get("age").getType());
    }
    
    @Test
    void shouldHandleNonExistentDto() {
        // When resolving a DTO that doesn't exist
        Schema<?> schema = resolver.resolveSchema("NonExistentDto");
        
        // Then it should return a placeholder schema
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getDescription().contains("Schema not available"));
        
        Map<String, Schema> properties = schema.getProperties();
        assertNotNull(properties);
        assertTrue(properties.containsKey("_schemaPlaceholder"));
    }
    
    @Test
    void shouldCacheResolvedSchemas() {
        // When resolving the same DTO multiple times
        Schema<?> schema1 = resolver.resolveSchema("TestCustomer");
        Schema<?> schema2 = resolver.resolveSchema("TestCustomer");
        
        // Then it should return the same cached instance
        assertSame(schema1, schema2);
    }
    
    @Test
    void shouldHandleNullProjectPath() {
        // Given a resolver with null project path
        DtoSchemaResolver nullResolver = new DtoSchemaResolver(null);
        
        // When resolving a DTO
        Schema<?> schema = nullResolver.resolveSchema("TestCustomer");
        
        // Then it should return a placeholder schema
        assertNotNull(schema);
        assertTrue(schema.getDescription().contains("Schema not available"));
    }
    
    @Test
    void shouldHandleEmptyProjectPath() {
        // Given a resolver with empty project path
        DtoSchemaResolver emptyResolver = new DtoSchemaResolver("");
        
        // When resolving a DTO
        Schema<?> schema = emptyResolver.resolveSchema("TestCustomer");
        
        // Then it should return a placeholder schema
        assertNotNull(schema);
        assertTrue(schema.getDescription().contains("Schema not available"));
    }
}