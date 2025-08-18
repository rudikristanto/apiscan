package com.apiscan.cli;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for complex schema scenarios like those found in Shopizer.
 */
public class ShopizerComplexSchemaTest {

    private DtoSchemaResolver resolver;
    private Path tempDir;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        this.resolver = new DtoSchemaResolver(tempDir.toString());
    }

    @Test
    public void testDeeplyNestedSchemaResolution() throws Exception {
        // Create complex nested DTOs similar to Shopizer's structure
        String customerContent = "package com.test;\n" +
            "\n" +
            "public class Customer {\n" +
            "    private String name;\n" +
            "    private CustomerGender gender;\n" +
            "    private java.util.List<CustomerAttribute> attributes;\n" +
            "    private java.util.List<Review> reviews;\n" +
            "    \n" +
            "    public String getName() { return name; }\n" +
            "    public CustomerGender getGender() { return gender; }\n" +
            "    public java.util.List<CustomerAttribute> getAttributes() { return attributes; }\n" +
            "    public java.util.List<Review> getReviews() { return reviews; }\n" +
            "}\n";

        String customerGenderContent = "package com.test;\n" +
            "\n" +
            "public class CustomerGender {\n" +
            "    private String code;\n" +
            "    private String name;\n" +
            "    \n" +
            "    public String getCode() { return code; }\n" +
            "    public String getName() { return name; }\n" +
            "}\n";

        String customerAttributeContent = "package com.test;\n" +
            "\n" +
            "public class CustomerAttribute {\n" +
            "    private String textValue;\n" +
            "    private ProductOption productOption;\n" +
            "    \n" +
            "    public String getTextValue() { return textValue; }\n" +
            "    public ProductOption getProductOption() { return productOption; }\n" +
            "}\n";

        String productOptionContent = "package com.test;\n" +
            "\n" +
            "public class ProductOption {\n" +
            "    private String code;\n" +
            "    private String name;\n" +
            "    \n" +
            "    public String getCode() { return code; }\n" +
            "    public String getName() { return name; }\n" +
            "}\n";

        String reviewContent = "package com.test;\n" +
            "\n" +
            "public class Review {\n" +
            "    private String comment;\n" +
            "    private Product product;\n" +
            "    \n" +
            "    public String getComment() { return comment; }\n" +
            "    public Product getProduct() { return product; }\n" +
            "}\n";

        String productContent = "package com.test;\n" +
            "\n" +
            "public class Product {\n" +
            "    private String name;\n" +
            "    private Customer owner;\n" +
            "    \n" +
            "    public String getName() { return name; }\n" +
            "    public Customer getOwner() { return owner; }\n" +
            "}\n";

        // Create test files
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("Customer.java"), customerContent.getBytes());
        Files.write(srcDir.resolve("CustomerGender.java"), customerGenderContent.getBytes());
        Files.write(srcDir.resolve("CustomerAttribute.java"), customerAttributeContent.getBytes());
        Files.write(srcDir.resolve("ProductOption.java"), productOptionContent.getBytes());
        Files.write(srcDir.resolve("Review.java"), reviewContent.getBytes());
        Files.write(srcDir.resolve("Product.java"), productContent.getBytes());

        // Resolve Customer schema (which has circular references)
        Schema<?> customerSchema = resolver.resolveSchema("Customer");
        
        assertNotNull(customerSchema);
        
        // Get all resolved schemas with increased depth
        Map<String, Schema<?>> allSchemas = resolver.getAllResolvedSchemas(7);
        
        // Verify all referenced schemas are resolved
        assertTrue(allSchemas.containsKey("Customer"), "Should contain Customer schema");
        assertTrue(allSchemas.containsKey("CustomerGender"), "Should contain CustomerGender schema");
        assertTrue(allSchemas.containsKey("CustomerAttribute"), "Should contain CustomerAttribute schema");
        assertTrue(allSchemas.containsKey("ProductOption"), "Should contain ProductOption schema");
        assertTrue(allSchemas.containsKey("Review"), "Should contain Review schema");
        assertTrue(allSchemas.containsKey("Product"), "Should contain Product schema");
        
        // Verify schemas use $ref instead of inline expansion
        Schema<?> customer = allSchemas.get("Customer");
        assertNotNull(customer.getProperties());
        
        Map<String, Schema> customerProps = customer.getProperties();
        assertTrue(customerProps.containsKey("gender"));
        assertTrue(customerProps.containsKey("attributes"));
        assertTrue(customerProps.containsKey("reviews"));
        
        // Gender should be a reference
        assertEquals("#/components/schemas/CustomerGender", customerProps.get("gender").get$ref());
        
        // Attributes should be an array with CustomerAttribute items reference
        Schema<?> attributesProp = customerProps.get("attributes");
        assertEquals("array", attributesProp.getType());
        assertEquals("#/components/schemas/CustomerAttribute", attributesProp.getItems().get$ref());
        
        // Reviews should be an array with Review items reference
        Schema<?> reviewsProp = customerProps.get("reviews");
        assertEquals("array", reviewsProp.getType());
        assertEquals("#/components/schemas/Review", reviewsProp.getItems().get$ref());
    }

    @Test
    public void testDuplicateRequiredFieldsRemoval() throws Exception {
        // Test schema that might generate duplicate required fields
        String dtoContent = "package com.test;\n" +
            "\n" +
            "import javax.validation.constraints.NotNull;\n" +
            "import javax.validation.constraints.NotEmpty;\n" +
            "\n" +
            "public class TestDto extends BaseDto {\n" +
            "    @NotNull\n" +
            "    private String field1;\n" +
            "    \n" +
            "    @NotEmpty\n" +
            "    private String field2;\n" +
            "    \n" +
            "    public String getField1() { return field1; }\n" +
            "    public String getField2() { return field2; }\n" +
            "}\n";

        String baseDtoContent = "package com.test;\n" +
            "\n" +
            "import javax.validation.constraints.NotNull;\n" +
            "\n" +
            "public class BaseDto {\n" +
            "    @NotNull\n" +
            "    private String field1;  // Same name as in child\n" +
            "    \n" +
            "    public String getField1() { return field1; }\n" +
            "}\n";

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("TestDto.java"), dtoContent.getBytes());
        Files.write(srcDir.resolve("BaseDto.java"), baseDtoContent.getBytes());

        Schema<?> schema = resolver.resolveSchema("TestDto");
        
        assertNotNull(schema);
        
        // Check that required fields don't have duplicates
        List<String> required = schema.getRequired();
        if (required != null) {
            // Count occurrences of each field
            long field1Count = required.stream().filter(f -> f.equals("field1")).count();
            assertEquals(1, field1Count, "field1 should appear only once in required list");
        }
    }

    @Test
    public void testInvalidSchemaNameHandling() throws Exception {
        // Test schemas with names that need sanitization
        String problematicContent = "package com.test;\n" +
            "\n" +
            "public class ProblematicDto {\n" +
            "    private byte[] data;\n" +
            "    private java.util.List<String> items;\n" +
            "    private java.util.Map<String, Object> properties;\n" +
            "    \n" +
            "    public byte[] getData() { return data; }\n" +
            "    public java.util.List<String> getItems() { return items; }\n" +
            "    public java.util.Map<String, Object> getProperties() { return properties; }\n" +
            "}\n";

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve("ProblematicDto.java"), problematicContent.getBytes());

        Schema<?> schema = resolver.resolveSchema("ProblematicDto");
        
        assertNotNull(schema);
        assertNotNull(schema.getProperties());
        
        // All properties should be resolved without issues
        Map<String, Schema> properties = schema.getProperties();
        assertTrue(properties.containsKey("data"));
        assertTrue(properties.containsKey("items"));
        assertTrue(properties.containsKey("properties"));
        
        // Data should be of appropriate type (not causing invalid schema names)
        Schema<?> dataField = properties.get("data");
        assertNotNull(dataField.getType());
        
        // Items should be array type
        assertEquals("array", properties.get("items").getType());
    }
}