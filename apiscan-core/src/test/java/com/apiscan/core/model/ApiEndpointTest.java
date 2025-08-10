package com.apiscan.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiEndpointTest {
    
    @Test
    void testApiEndpointCreation() {
        ApiEndpoint endpoint = new ApiEndpoint();
        
        endpoint.setHttpMethod("GET");
        endpoint.setPath("/api/users");
        endpoint.setControllerClass("UserController");
        endpoint.setMethodName("getUsers");
        endpoint.setOperationId("UserController_getUsers");
        endpoint.setDescription("Get all users");
        endpoint.setDeprecated(false);
        
        assertEquals("GET", endpoint.getHttpMethod());
        assertEquals("/api/users", endpoint.getPath());
        assertEquals("UserController", endpoint.getControllerClass());
        assertEquals("getUsers", endpoint.getMethodName());
        assertEquals("UserController_getUsers", endpoint.getOperationId());
        assertEquals("Get all users", endpoint.getDescription());
        assertFalse(endpoint.isDeprecated());
    }
    
    @Test
    void testParameterCreation() {
        ApiEndpoint.Parameter param = new ApiEndpoint.Parameter();
        
        param.setName("userId");
        param.setType("Integer");
        param.setIn("path");
        param.setRequired(true);
        param.setDescription("User identifier");
        
        assertEquals("userId", param.getName());
        assertEquals("Integer", param.getType());
        assertEquals("path", param.getIn());
        assertTrue(param.isRequired());
        assertEquals("User identifier", param.getDescription());
    }
    
    @Test
    void testRequestBodyCreation() {
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        
        requestBody.setRequired(true);
        requestBody.setDescription("User data");
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("UserDto");
        
        requestBody.getContent().put("application/json", mediaType);
        
        assertTrue(requestBody.isRequired());
        assertEquals("User data", requestBody.getDescription());
        assertTrue(requestBody.getContent().containsKey("application/json"));
        assertEquals("UserDto", requestBody.getContent().get("application/json").getSchema());
    }
    
    @Test
    void testResponseCreation() {
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        
        response.setDescription("Successful response");
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("User");
        
        response.getContent().put("application/json", mediaType);
        
        assertEquals("Successful response", response.getDescription());
        assertTrue(response.getContent().containsKey("application/json"));
        assertEquals("User", response.getContent().get("application/json").getSchema());
    }
    
    @Test
    void testEndpointWithComplexData() {
        ApiEndpoint endpoint = new ApiEndpoint();
        
        endpoint.setHttpMethod("POST");
        endpoint.setPath("/api/users/{id}/orders");
        endpoint.setControllerClass("OrderController");
        endpoint.setMethodName("createOrder");
        
        // Add path parameter
        ApiEndpoint.Parameter pathParam = new ApiEndpoint.Parameter();
        pathParam.setName("id");
        pathParam.setType("Integer");
        pathParam.setIn("path");
        pathParam.setRequired(true);
        endpoint.getParameters().add(pathParam);
        
        // Add query parameter
        ApiEndpoint.Parameter queryParam = new ApiEndpoint.Parameter();
        queryParam.setName("priority");
        queryParam.setType("String");
        queryParam.setIn("query");
        queryParam.setRequired(false);
        endpoint.getParameters().add(queryParam);
        
        // Add request body
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        requestBody.setRequired(true);
        ApiEndpoint.MediaType requestMediaType = new ApiEndpoint.MediaType();
        requestMediaType.setSchema("OrderDto");
        requestBody.getContent().put("application/json", requestMediaType);
        endpoint.setRequestBody(requestBody);
        
        // Add response
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        response.setDescription("Order created");
        ApiEndpoint.MediaType responseMediaType = new ApiEndpoint.MediaType();
        responseMediaType.setSchema("Order");
        response.getContent().put("application/json", responseMediaType);
        endpoint.getResponses().put("201", response);
        
        // Add consumes and produces
        endpoint.getConsumes().add("application/json");
        endpoint.getProduces().add("application/json");
        
        // Verify all data
        assertEquals("POST", endpoint.getHttpMethod());
        assertEquals("/api/users/{id}/orders", endpoint.getPath());
        assertEquals(2, endpoint.getParameters().size());
        assertNotNull(endpoint.getRequestBody());
        assertEquals(1, endpoint.getResponses().size());
        assertTrue(endpoint.getConsumes().contains("application/json"));
        assertTrue(endpoint.getProduces().contains("application/json"));
    }
}