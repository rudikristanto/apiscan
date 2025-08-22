package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class ShopizerOrdersEndpointTest {
    
    private SwaggerCoreOpenApiGenerator generator;
    private ObjectMapper yamlMapper;
    
    @BeforeEach
    void setUp() {
        generator = new SwaggerCoreOpenApiGenerator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    @Test
    void testShopizerOrdersEndpointWithQueryParameters() throws Exception {
        // Test the exact scenario from Shopizer: GET /api/v1/private/orders/customers/{id}
        // with path parameter 'id' and query parameters 'start' and 'count'
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("shopizer");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("OrderApi");
        endpoint.setMethodName("list");
        endpoint.setPath("/api/v1/private/orders/customers/{id}");
        endpoint.setHttpMethod("GET");
        
        // Add path parameter 'id'
        ApiEndpoint.Parameter idParam = new ApiEndpoint.Parameter();
        idParam.setName("id");
        idParam.setType("Long");
        idParam.setIn("path");
        idParam.setRequired(true);
        endpoint.getParameters().add(idParam);
        
        // Add query parameter 'start' (optional)
        ApiEndpoint.Parameter startParam = new ApiEndpoint.Parameter();
        startParam.setName("start");
        startParam.setType("Integer");
        startParam.setIn("query");
        startParam.setRequired(false);
        endpoint.getParameters().add(startParam);
        
        // Add query parameter 'count' (optional)
        ApiEndpoint.Parameter countParam = new ApiEndpoint.Parameter();
        countParam.setName("count");
        countParam.setType("Integer");
        countParam.setIn("query");
        countParam.setRequired(false);
        endpoint.getParameters().add(countParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        // Verify the endpoint exists
        JsonNode pathsNode = specNode.get("paths");
        assertThat(pathsNode).isNotNull();
        
        JsonNode pathItem = pathsNode.get("/api/v1/private/orders/customers/{id}");
        assertThat(pathItem).isNotNull();
        
        JsonNode operation = pathItem.get("get");
        assertThat(operation).isNotNull();
        
        // Verify all parameters are included
        JsonNode parameters = operation.get("parameters");
        assertThat(parameters).isNotNull();
        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters).hasSize(3); // Should have 3 parameters: id (path), start (query), count (query)
        
        // Check parameters exist
        boolean hasIdPathParam = false;
        boolean hasStartQueryParam = false;
        boolean hasCountQueryParam = false;
        
        for (JsonNode param : parameters) {
            String name = param.get("name").asText();
            String in = param.get("in").asText();
            
            if ("id".equals(name) && "path".equals(in)) {
                hasIdPathParam = true;
                assertThat(param.get("required").asBoolean()).isTrue();
            } else if ("start".equals(name) && "query".equals(in)) {
                hasStartQueryParam = true;
                assertThat(param.get("required").asBoolean()).isFalse();
            } else if ("count".equals(name) && "query".equals(in)) {
                hasCountQueryParam = true;
                assertThat(param.get("required").asBoolean()).isFalse();
            }
        }
        
        assertThat(hasIdPathParam).isTrue();
        assertThat(hasStartQueryParam).isTrue();
        assertThat(hasCountQueryParam).isTrue();
        
        // Validate that the fix preserves both path and query parameters
        System.out.println("✅ VERIFIED: Query parameters 'start' and 'count' are correctly included");
        System.out.println("✅ VERIFIED: Path parameter 'id' is correctly included");
        System.out.println("✅ FIX CONFIRMED: The issue where query parameters were filtered out is resolved");
    }
}