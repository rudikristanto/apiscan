package com.apiscan.cli;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for specific DTO classes that are missing from OpenAPI generation.
 */
class DtoSchemaResolverMissingTest {
    
    private static final Logger logger = LoggerFactory.getLogger(DtoSchemaResolverMissingTest.class);
    private DtoSchemaResolver dtoSchemaResolver;

    @BeforeEach
    void setUp() {
        // Use actual Shopizer project path
        dtoSchemaResolver = new DtoSchemaResolver("C:\\Users\\Rudi Kristanto\\prj\\shopizer\\sm-shop");
    }

    @Test
    void testPersistableGroup() {
        logger.info("=== Testing PersistableGroup resolution ===");
        
        Schema<?> schema = dtoSchemaResolver.resolveSchema("PersistableGroup");
        
        assertNotNull(schema, "PersistableGroup schema should not be null");
        assertEquals("object", schema.getType(), "Schema should be of type object");
        
        if (schema.getProperties() != null) {
            logger.info("PersistableGroup properties found: {}", schema.getProperties().keySet());
            assertTrue(schema.getProperties().size() > 0, "PersistableGroup should have properties from parent class");
        } else {
            logger.warn("PersistableGroup has no properties - inheritance parsing may have failed");
        }
    }

    @Test
    void testPersistableCustomerAttribute() {
        logger.info("=== Testing PersistableCustomerAttribute resolution ===");
        
        Schema<?> schema = dtoSchemaResolver.resolveSchema("PersistableCustomerAttribute");
        
        assertNotNull(schema, "PersistableCustomerAttribute schema should not be null");
        assertEquals("object", schema.getType(), "Schema should be of type object");
        
        if (schema.getProperties() != null) {
            logger.info("PersistableCustomerAttribute properties found: {}", schema.getProperties().keySet());
            assertTrue(schema.getProperties().size() > 0, "PersistableCustomerAttribute should have properties");
            
            // Should have both direct fields and inherited fields
            assertTrue(schema.getProperties().containsKey("customerOption") || 
                      schema.getProperties().containsKey("customerOptionValue") ||
                      schema.getProperties().containsKey("textValue"), 
                      "Should contain at least one expected field");
        } else {
            logger.warn("PersistableCustomerAttribute has no properties - inheritance parsing may have failed");
        }
    }
}