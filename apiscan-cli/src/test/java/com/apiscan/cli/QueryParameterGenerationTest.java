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

class QueryParameterGenerationTest {
    
    private SwaggerCoreOpenApiGenerator generator;
    private ObjectMapper yamlMapper;
    
    @BeforeEach
    void setUp() {
        generator = new SwaggerCoreOpenApiGenerator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    @Test
    void testQueryParametersWithPathParametersIncluded() throws Exception {
        // Create a scan result with an endpoint that has both path and query parameters
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("test-project");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("OrderController");
        endpoint.setMethodName("getCustomerOrders");
        endpoint.setPath("/api/v1/orders/customers/{id}");
        endpoint.setHttpMethod("GET");
        
        // Add path parameter
        ApiEndpoint.Parameter pathParam = new ApiEndpoint.Parameter();
        pathParam.setName("id");
        pathParam.setType("Long");
        pathParam.setIn("path");
        pathParam.setRequired(true);
        endpoint.getParameters().add(pathParam);
        
        // Add query parameters
        ApiEndpoint.Parameter startParam = new ApiEndpoint.Parameter();
        startParam.setName("start");
        startParam.setType("Integer");
        startParam.setIn("query");
        startParam.setRequired(false);
        endpoint.getParameters().add(startParam);
        
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
        
        JsonNode pathItem = pathsNode.get("/api/v1/orders/customers/{id}");
        assertThat(pathItem).isNotNull();
        
        JsonNode operation = pathItem.get("get");
        assertThat(operation).isNotNull();
        
        // Verify all parameters are included
        JsonNode parameters = operation.get("parameters");
        assertThat(parameters).isNotNull();
        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters).hasSize(3); // Should have 3 parameters: id (path), start (query), count (query)
        
        // Check path parameter
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
    }
}