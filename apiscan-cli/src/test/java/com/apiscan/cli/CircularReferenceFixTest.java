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
 * Tests for circular reference fixes in DtoSchemaResolver.
 */
public class CircularReferenceFixTest {

    private DtoSchemaResolver resolver;
    private Path tempDir;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        this.tempDir = tempDir;
        this.resolver = new DtoSchemaResolver(tempDir.toString());
    }

    @Test
    public void testCircularReferenceCreatesProperRefs() throws Exception {
        // Create DTOs with circular references (Customer -> Address -> Customer)
        String customerContent = """
            package com.test;
            
            public class Customer {
                private String name;
                private Address billingAddress;
                private java.util.List<Order> orders;
                
                public String getName() { return name; }
                public Address getBillingAddress() { return billingAddress; }
                public java.util.List<Order> getOrders() { return orders; }
            }
            """;

        String addressContent = """
            package com.test;
            
            public class Address {
                private String street;
                private Customer customer;
                
                public String getStreet() { return street; }
                public Customer getCustomer() { return customer; }
            }
            """;

        String orderContent = """
            package com.test;
            
            public class Order {
                private String orderNumber;
                private Customer customer;
                
                public String getOrderNumber() { return orderNumber; }
                public Customer getCustomer() { return customer; }
            }
            """;

        // Create test files
        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Customer.java"), customerContent);
        Files.writeString(srcDir.resolve("Address.java"), addressContent);
        Files.writeString(srcDir.resolve("Order.java"), orderContent);

        // Resolve Customer schema
        Schema<?> customerSchema = resolver.resolveSchema("Customer");
        
        assertNotNull(customerSchema);
        assertNotNull(customerSchema.getProperties());
        
        Map<String, Schema> properties = customerSchema.getProperties();
        
        // Verify basic fields are included
        assertTrue(properties.containsKey("name"));
        assertTrue(properties.containsKey("billingAddress"));
        assertTrue(properties.containsKey("orders"));
        
        // Verify billingAddress is a reference, not inline expansion
        Schema<?> addressProperty = properties.get("billingAddress");
        assertNotNull(addressProperty.get$ref(), "billingAddress should be a $ref");
        assertEquals("#/components/schemas/Address", addressProperty.get$ref());
        
        // Verify orders array has proper item reference
        Schema<?> ordersProperty = properties.get("orders");
        assertEquals("array", ordersProperty.getType());
        assertNotNull(ordersProperty.getItems());
        assertNotNull(ordersProperty.getItems().get$ref(), "orders items should be a $ref");
        assertEquals("#/components/schemas/Order", ordersProperty.getItems().get$ref());
    }

    @Test
    public void testGetAllResolvedSchemasHandlesCircularRefs() throws Exception {
        // Create the same circular reference DTOs
        String customerContent = """
            package com.test;
            
            public class Customer {
                private String name;
                private Address address;
                
                public String getName() { return name; }
                public Address getAddress() { return address; }
            }
            """;

        String addressContent = """
            package com.test;
            
            public class Address {
                private String street;
                private Customer customer;
                
                public String getStreet() { return street; }
                public Customer getCustomer() { return customer; }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Customer.java"), customerContent);
        Files.writeString(srcDir.resolve("Address.java"), addressContent);

        // First resolve Customer schema
        resolver.resolveSchema("Customer");
        
        // Get all resolved schemas (should handle circular references properly)
        Map<String, Schema<?>> allSchemas = resolver.getAllResolvedSchemas(3);
        
        // Should contain both Customer and Address schemas
        assertTrue(allSchemas.containsKey("Customer"), "Should contain Customer schema");
        assertTrue(allSchemas.containsKey("Address"), "Should contain Address schema");
        
        // Verify Customer schema has proper reference to Address
        Schema<?> customerSchema = allSchemas.get("Customer");
        assertNotNull(customerSchema);
        Map<String, Schema> customerProps = customerSchema.getProperties();
        assertTrue(customerProps.containsKey("address"));
        assertEquals("#/components/schemas/Address", customerProps.get("address").get$ref());
        
        // Verify Address schema has proper reference to Customer  
        Schema<?> addressSchema = allSchemas.get("Address");
        assertNotNull(addressSchema);
        Map<String, Schema> addressProps = addressSchema.getProperties();
        assertTrue(addressProps.containsKey("customer"));
        assertEquals("#/components/schemas/Customer", addressProps.get("customer").get$ref());
    }

    @Test
    public void testDepthLimitPreventsInfiniteResolution() throws Exception {
        // Create deeply nested DTOs
        String level1Content = """
            package com.test;
            
            public class Level1 {
                private String data;
                private Level2 nested;
                
                public String getData() { return data; }
                public Level2 getNested() { return nested; }
            }
            """;

        String level2Content = """
            package com.test;
            
            public class Level2 {
                private String data;
                private Level3 nested;
                
                public String getData() { return data; }
                public Level3 getNested() { return nested; }
            }
            """;

        String level3Content = """
            package com.test;
            
            public class Level3 {
                private String data;
                private Level1 backToStart;
                
                public String getData() { return data; }
                public Level1 getBackToStart() { return backToStart; }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("Level1.java"), level1Content);
        Files.writeString(srcDir.resolve("Level2.java"), level2Content);
        Files.writeString(srcDir.resolve("Level3.java"), level3Content);

        // Resolve with limited depth
        resolver.resolveSchema("Level1");
        Map<String, Schema<?>> allSchemas = resolver.getAllResolvedSchemas(2);
        
        // Should complete without infinite recursion
        assertTrue(allSchemas.containsKey("Level1"));
        assertTrue(allSchemas.containsKey("Level2"));
        assertTrue(allSchemas.containsKey("Level3"));
        
        // Verify all schemas use references, not inline expansion
        Schema<?> level1 = allSchemas.get("Level1");
        assertEquals("#/components/schemas/Level2", level1.getProperties().get("nested").get$ref());
        
        Schema<?> level2 = allSchemas.get("Level2");  
        assertEquals("#/components/schemas/Level3", level2.getProperties().get("nested").get$ref());
        
        Schema<?> level3 = allSchemas.get("Level3");
        assertEquals("#/components/schemas/Level1", level3.getProperties().get("backToStart").get$ref());
    }

    @Test
    public void testPlaceholderSchemaForCircularReferenceDuringResolution() throws Exception {
        // Test direct circular reference in same resolve call
        String selfRefContent = """
            package com.test;
            
            public class SelfRef {
                private String name;
                private SelfRef parent;
                private java.util.List<SelfRef> children;
                
                public String getName() { return name; }
                public SelfRef getParent() { return parent; }
                public java.util.List<SelfRef> getChildren() { return children; }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("SelfRef.java"), selfRefContent);

        // Resolve schema
        Schema<?> schema = resolver.resolveSchema("SelfRef");
        
        assertNotNull(schema);
        Map<String, Schema> properties = schema.getProperties();
        
        // Should have all properties
        assertTrue(properties.containsKey("name"));
        assertTrue(properties.containsKey("parent"));
        assertTrue(properties.containsKey("children"));
        
        // Parent should be a reference
        assertEquals("#/components/schemas/SelfRef", properties.get("parent").get$ref());
        
        // Children array should have SelfRef items
        Schema<?> childrenProp = properties.get("children");
        assertEquals("array", childrenProp.getType());
        assertEquals("#/components/schemas/SelfRef", childrenProp.getItems().get$ref());
    }
}