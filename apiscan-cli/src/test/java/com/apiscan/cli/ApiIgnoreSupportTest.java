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
 * Tests for @ApiIgnore annotation support in DtoSchemaResolver.
 */
public class ApiIgnoreSupportTest {

    private DtoSchemaResolver resolver;
    private Path tempDir;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        this.resolver = new DtoSchemaResolver(tempDir.toString());
    }

    @Test
    public void testApiIgnoreAnnotationExcludesField() throws Exception {
        // Create a test DTO with @ApiIgnore field
        String dtoContent = "package com.test;\n" +
            "\n" +
            "public class TestDto {\n" +
            "    private String visibleField;\n" +
            "    \n" +
            "    @io.swagger.v3.oas.annotations.Hidden\n" +
            "    private String hiddenField;\n" +
            "    \n" +
            "    @com.fasterxml.jackson.annotation.JsonIgnore\n" +
            "    private String jsonIgnoredField;\n" +
            "    \n" +
            "    @ApiIgnore\n" +
            "    private String apiIgnoredField;\n" +
            "    \n" +
            "    public String getVisibleField() { return visibleField; }\n" +
            "    public String getHiddenField() { return hiddenField; }\n" +
            "    public String getJsonIgnoredField() { return jsonIgnoredField; }\n" +
            "    public String getApiIgnoredField() { return apiIgnoredField; }\n" +
            "}\n";

        // Create test directory structure
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("TestDto.java"), dtoContent.getBytes());

        // Resolve the schema
        Schema<?> schema = resolver.resolveSchema("TestDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        // Verify only visible field is included
        Map<String, Schema> properties = schema.getProperties();
        assertTrue(properties.containsKey("visibleField"), "visibleField should be included");
        assertFalse(properties.containsKey("hiddenField"), "@Hidden field should be excluded");
        assertFalse(properties.containsKey("jsonIgnoredField"), "@JsonIgnore field should be excluded");
        assertFalse(properties.containsKey("apiIgnoredField"), "@ApiIgnore field should be excluded");
        
        assertEquals(1, properties.size(), "Should only have 1 visible field");
    }

    @Test
    public void testApiIgnoreWorksWithInheritance() throws Exception {
        // Create parent class with @ApiIgnore field
        String parentContent = """
            package com.test;
            
            public class ParentDto {
                private String parentVisibleField;
                
                @ApiIgnore
                private String parentIgnoredField;
                
                public String getParentVisibleField() { return parentVisibleField; }
                public String getParentIgnoredField() { return parentIgnoredField; }
            }
            """;

        // Create child class extending parent
        String childContent = """
            package com.test;
            
            public class ChildDto extends ParentDto {
                private String childField;
                
                @JsonIgnore
                private String childIgnoredField;
                
                public String getChildField() { return childField; }
                public String getChildIgnoredField() { return childIgnoredField; }
            }
            """;

        // Create test files
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("ParentDto.java"), parentContent);
        Files.writeString(srcDir.resolve("ChildDto.java"), childContent);

        // Resolve the child schema
        Schema<?> schema = resolver.resolveSchema("ChildDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        Map<String, Schema> properties = schema.getProperties();
        assertTrue(properties.containsKey("parentVisibleField"), "Parent visible field should be included");
        assertTrue(properties.containsKey("childField"), "Child field should be included");
        assertFalse(properties.containsKey("parentIgnoredField"), "Parent @ApiIgnore field should be excluded");
        assertFalse(properties.containsKey("childIgnoredField"), "Child @JsonIgnore field should be excluded");
        
        assertEquals(2, properties.size(), "Should have 2 visible fields");
    }

    @Test
    public void testNoApiIgnoreFieldsIncludesAllFields() throws Exception {
        // Create a DTO without any @ApiIgnore annotations
        String dtoContent = """
            package com.test;
            
            public class RegularDto {
                private String field1;
                private int field2;
                private boolean field3;
                
                public String getField1() { return field1; }
                public int getField2() { return field2; }
                public boolean getField3() { return field3; }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("RegularDto.java"), dtoContent);

        Schema<?> schema = resolver.resolveSchema("RegularDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        Map<String, Schema> properties = schema.getProperties();
        assertEquals(3, properties.size(), "Should include all 3 fields");
        assertTrue(properties.containsKey("field1"));
        assertTrue(properties.containsKey("field2"));
        assertTrue(properties.containsKey("field3"));
    }
}