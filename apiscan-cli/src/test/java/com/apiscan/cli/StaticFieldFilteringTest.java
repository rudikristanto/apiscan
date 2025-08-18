package com.apiscan.cli;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for filtering out inappropriate fields from DTO schemas.
 * Ensures static fields, serialVersionUID, and other problematic fields are excluded.
 */
public class StaticFieldFilteringTest {

    private DtoSchemaResolver resolver;
    private Path tempDir;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        this.resolver = new DtoSchemaResolver(tempDir.toString());
    }

    @Test
    public void testStaticFieldsExcluded() throws Exception {
        // Create a DTO with static fields that should be excluded
        String dtoContent = """
            package com.test;
            
            import java.util.Collator;
            
            public class TestDto {
                // Static fields - should NOT be included
                public static final long serialVersionUID = 1L;
                public static final Collator DEFAULT_STRING_COLLATOR = Collator.getInstance();
                public static String DEFAULT_VALUE = "test";
                
                // Instance fields - should be included
                private String name;
                private int id;
                
                // Getters
                public String getName() { return name; }
                public int getId() { return id; }
            }
            """;

        // Create test file
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("TestDto.java"), dtoContent.getBytes());

        // Resolve schema
        Schema<?> schema = resolver.resolveSchema("TestDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        Map<String, Schema> properties = schema.getProperties();
        
        // Should include instance fields
        assertTrue(properties.containsKey("name"), "Should include 'name' field");
        assertTrue(properties.containsKey("id"), "Should include 'id' field");
        
        // Should NOT include static fields
        assertFalse(properties.containsKey("serialVersionUID"), 
            "Should NOT include 'serialVersionUID' static field");
        assertFalse(properties.containsKey("DEFAULT_STRING_COLLATOR"), 
            "Should NOT include 'DEFAULT_STRING_COLLATOR' static field");
        assertFalse(properties.containsKey("DEFAULT_VALUE"), 
            "Should NOT include 'DEFAULT_VALUE' static field");
        
        // Should have exactly 2 properties
        assertEquals(2, properties.size(), "Should have exactly 2 properties (only instance fields)");
    }

    @Test
    public void testProblematicFieldNamesExcluded() throws Exception {
        // Create a DTO with fields that have problematic names
        String dtoContent = """
            package com.test;
            
            import java.util.Collator;
            
            public class ProblematicDto {
                // Problematic field names - should NOT be included
                private Collator DEFAULT_STRING_COLLATOR;
                private String DEFAULT_LOCALE;
                private String SOME_COLLATOR_VALUE;
                
                // Normal fields - should be included
                private String normalField;
                private int id;
                
                // Getters for ALL fields (but problematic ones should still be filtered)
                public Collator getDEFAULT_STRING_COLLATOR() { return DEFAULT_STRING_COLLATOR; }
                public String getDEFAULT_LOCALE() { return DEFAULT_LOCALE; }
                public String getSOME_COLLATOR_VALUE() { return SOME_COLLATOR_VALUE; }
                public String getNormalField() { return normalField; }
                public int getId() { return id; }
            }
            """;

        // Create test file
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("ProblematicDto.java"), dtoContent.getBytes());

        // Resolve schema
        Schema<?> schema = resolver.resolveSchema("ProblematicDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        Map<String, Schema> properties = schema.getProperties();
        
        // Should include normal fields
        assertTrue(properties.containsKey("normalField"), "Should include 'normalField'");
        assertTrue(properties.containsKey("id"), "Should include 'id' field");
        
        // Should NOT include problematic field names
        assertFalse(properties.containsKey("DEFAULT_STRING_COLLATOR"), 
            "Should NOT include 'DEFAULT_STRING_COLLATOR' field");
        assertFalse(properties.containsKey("DEFAULT_LOCALE"), 
            "Should NOT include 'DEFAULT_LOCALE' field");
        assertFalse(properties.containsKey("SOME_COLLATOR_VALUE"), 
            "Should NOT include 'SOME_COLLATOR_VALUE' field");
        
        // Should have exactly 2 properties
        assertEquals(2, properties.size(), "Should have exactly 2 properties (only normal fields)");
    }

    @Test 
    public void testPersistableCustomerAttributeSimplified() throws Exception {
        // Simulate simplified version of PersistableCustomerAttribute
        String customerAttributeContent = """
            package com.test;
            
            import java.util.Collator;
            
            public class PersistableCustomerAttribute {
                // Should NOT be included
                private static final long serialVersionUID = 1L;
                public static final Collator DEFAULT_STRING_COLLATOR = Collator.getInstance();
                
                // Should be included
                private String textValue;
                private CustomerOption customerOption;
                private CustomerOptionValue customerOptionValue;
                private Long id;
                
                public String getTextValue() { return textValue; }
                public CustomerOption getCustomerOption() { return customerOption; }
                public CustomerOptionValue getCustomerOptionValue() { return customerOptionValue; }
                public Long getId() { return id; }
            }
            """;

        String customerOptionContent = """
            package com.test;
            
            public class CustomerOption {
                private Long id;
                private String code;
                
                public Long getId() { return id; }
                public String getCode() { return code; }
            }
            """;

        String customerOptionValueContent = """
            package com.test;
            
            public class CustomerOptionValue {
                private Long id;
                private String code;
                
                public Long getId() { return id; }
                public String getCode() { return code; }
            }
            """;

        // Create test files
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("PersistableCustomerAttribute.java"), customerAttributeContent.getBytes());
        Files.write(srcDir.resolve("CustomerOption.java"), customerOptionContent.getBytes());
        Files.write(srcDir.resolve("CustomerOptionValue.java"), customerOptionValueContent.getBytes());

        // Resolve schema
        Schema<?> schema = resolver.resolveSchema("PersistableCustomerAttribute");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        Map<String, Schema> properties = schema.getProperties();
        
        // Should include expected fields
        assertTrue(properties.containsKey("textValue"), "Should include 'textValue'");
        assertTrue(properties.containsKey("customerOption"), "Should include 'customerOption'");
        assertTrue(properties.containsKey("customerOptionValue"), "Should include 'customerOptionValue'");
        assertTrue(properties.containsKey("id"), "Should include 'id'");
        
        // Should NOT include problematic fields
        assertFalse(properties.containsKey("serialVersionUID"), "Should NOT include 'serialVersionUID'");
        assertFalse(properties.containsKey("DEFAULT_STRING_COLLATOR"), "Should NOT include 'DEFAULT_STRING_COLLATOR'");
        
        // Should have exactly 4 properties
        assertEquals(4, properties.size(), "Should have exactly 4 properties");
        
        // Verify CustomerOption and CustomerOptionValue are properly referenced
        assertEquals("#/components/schemas/CustomerOption", properties.get("customerOption").get$ref());
        assertEquals("#/components/schemas/CustomerOptionValue", properties.get("customerOptionValue").get$ref());
    }
}