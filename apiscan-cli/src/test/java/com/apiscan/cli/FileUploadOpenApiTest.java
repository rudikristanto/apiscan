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

class FileUploadOpenApiTest {
    
    private SwaggerCoreOpenApiGenerator generator;
    private ObjectMapper yamlMapper;
    
    @BeforeEach
    void setUp() {
        generator = new SwaggerCoreOpenApiGenerator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    @Test
    void testSingleFileUploadOpenApiGeneration() throws Exception {
        // Create a scan result with a file upload endpoint
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("file-upload-test");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("FileController");
        endpoint.setMethodName("upload");
        endpoint.setPath("/api/v1/private/file");
        endpoint.setHttpMethod("POST");
        
        // Add file parameter
        ApiEndpoint.Parameter fileParam = new ApiEndpoint.Parameter();
        fileParam.setName("file");
        fileParam.setType("MultipartFile");
        fileParam.setIn("formData");
        fileParam.setRequired(true);
        endpoint.getParameters().add(fileParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        // Verify the endpoint exists
        JsonNode pathsNode = specNode.get("paths");
        assertThat(pathsNode).isNotNull();
        
        JsonNode pathItem = pathsNode.get("/api/v1/private/file");
        assertThat(pathItem).isNotNull();
        
        JsonNode operation = pathItem.get("post");
        assertThat(operation).isNotNull();
        
        // Verify no file parameters in parameters array
        JsonNode parameters = operation.get("parameters");
        if (parameters != null) {
            assertThat(parameters).isEmpty(); // File parameters should not be in parameters
        }
        
        // Verify multipart/form-data request body
        JsonNode requestBody = operation.get("requestBody");
        assertThat(requestBody).isNotNull();
        assertThat(requestBody.get("description").asText()).isEqualTo("Multipart form data");
        assertThat(requestBody.get("required").asBoolean()).isTrue();
        
        JsonNode content = requestBody.get("content");
        assertThat(content).isNotNull();
        
        JsonNode multipartContent = content.get("multipart/form-data");
        assertThat(multipartContent).isNotNull();
        
        JsonNode schema = multipartContent.get("schema");
        assertThat(schema).isNotNull();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        
        JsonNode properties = schema.get("properties");
        assertThat(properties).isNotNull();
        
        JsonNode fileProperty = properties.get("file");
        assertThat(fileProperty).isNotNull();
        assertThat(fileProperty.get("type").asText()).isEqualTo("string");
        assertThat(fileProperty.get("format").asText()).isEqualTo("binary");
        
        JsonNode required = schema.get("required");
        assertThat(required).isNotNull();
        assertThat(required.isArray()).isTrue();
        assertThat(required).hasSize(1);
        assertThat(required.get(0).asText()).isEqualTo("file");
        
        // Verify encoding information for proper Swagger UI rendering
        JsonNode encoding = multipartContent.get("encoding");
        assertThat(encoding).isNotNull();
        JsonNode fileEncoding = encoding.get("file");
        assertThat(fileEncoding).isNotNull();
        assertThat(fileEncoding.get("contentType").asText()).isEqualTo("application/octet-stream");
    }
    
    @Test
    void testMultipleFileUploadOpenApiGeneration() throws Exception {
        // Create a scan result with a multiple file upload endpoint
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("file-upload-test");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("FileController");
        endpoint.setMethodName("uploadMultiple");
        endpoint.setPath("/api/v1/private/files");
        endpoint.setHttpMethod("POST");
        
        // Add file array parameter
        ApiEndpoint.Parameter fileParam = new ApiEndpoint.Parameter();
        fileParam.setName("file[]");
        fileParam.setType("MultipartFile[]");
        fileParam.setIn("formData");
        fileParam.setRequired(true);
        endpoint.getParameters().add(fileParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        JsonNode operation = specNode.get("paths").get("/api/v1/private/files").get("post");
        JsonNode requestBody = operation.get("requestBody");
        JsonNode schema = requestBody.get("content").get("multipart/form-data").get("schema");
        JsonNode properties = schema.get("properties");
        JsonNode fileProperty = properties.get("file[]");
        
        // Verify array of files
        assertThat(fileProperty.get("type").asText()).isEqualTo("array");
        
        JsonNode items = fileProperty.get("items");
        assertThat(items).isNotNull();
        assertThat(items.get("type").asText()).isEqualTo("string");
        assertThat(items.get("format").asText()).isEqualTo("binary");
        
        // Verify encoding information for file arrays
        JsonNode multipartContent = specNode.get("paths").get("/api/v1/private/files").get("post")
                .get("requestBody").get("content").get("multipart/form-data");
        JsonNode encoding = multipartContent.get("encoding");
        assertThat(encoding).isNotNull();
        JsonNode fileEncoding = encoding.get("file[]");
        assertThat(fileEncoding).isNotNull();
        assertThat(fileEncoding.get("contentType").asText()).isEqualTo("application/octet-stream");
    }
    
    @Test
    void testMixedParametersWithFileUpload() throws Exception {
        // Create a scan result with mixed parameters including file upload
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("file-upload-test");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("DocumentController");
        endpoint.setMethodName("uploadDocument");
        endpoint.setPath("/documents/{categoryId}");
        endpoint.setHttpMethod("POST");
        
        // Add path parameter
        ApiEndpoint.Parameter pathParam = new ApiEndpoint.Parameter();
        pathParam.setName("categoryId");
        pathParam.setType("Long");
        pathParam.setIn("path");
        pathParam.setRequired(true);
        endpoint.getParameters().add(pathParam);
        
        // Add file parameter
        ApiEndpoint.Parameter fileParam = new ApiEndpoint.Parameter();
        fileParam.setName("file");
        fileParam.setType("MultipartFile");
        fileParam.setIn("formData");
        fileParam.setRequired(true);
        endpoint.getParameters().add(fileParam);
        
        // Add query parameter
        ApiEndpoint.Parameter queryParam = new ApiEndpoint.Parameter();
        queryParam.setName("description");
        queryParam.setType("String");
        queryParam.setIn("query");
        queryParam.setRequired(false);
        endpoint.getParameters().add(queryParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        JsonNode operation = specNode.get("paths").get("/documents/{categoryId}").get("post");
        
        // Verify parameters only include path and query parameters (not file)
        JsonNode parameters = operation.get("parameters");
        assertThat(parameters).isNotNull();
        assertThat(parameters).hasSize(2); // categoryId (path) + description (query)
        
        boolean hasPathParam = false;
        boolean hasQueryParam = false;
        
        for (JsonNode param : parameters) {
            String name = param.get("name").asText();
            String in = param.get("in").asText();
            
            if ("categoryId".equals(name) && "path".equals(in)) {
                hasPathParam = true;
            } else if ("description".equals(name) && "query".equals(in)) {
                hasQueryParam = true;
            }
        }
        
        assertThat(hasPathParam).isTrue();
        assertThat(hasQueryParam).isTrue();
        
        // Verify multipart request body for file
        JsonNode requestBody = operation.get("requestBody");
        assertThat(requestBody).isNotNull();
        
        JsonNode multipartFormData = requestBody.get("content").get("multipart/form-data");
        JsonNode fileProperty = multipartFormData.get("schema").get("properties").get("file");
        assertThat(fileProperty).isNotNull();
        assertThat(fileProperty.get("type").asText()).isEqualTo("string");
        assertThat(fileProperty.get("format").asText()).isEqualTo("binary");
        
        // Verify encoding information for mixed parameter scenario
        JsonNode encoding = multipartFormData.get("encoding");
        assertThat(encoding).isNotNull();
        JsonNode fileEncoding = encoding.get("file");
        assertThat(fileEncoding).isNotNull();
        assertThat(fileEncoding.get("contentType").asText()).isEqualTo("application/octet-stream");
    }
    
    @Test
    void testOptionalFileUpload() throws Exception {
        // Test file upload parameter that is not required
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("file-upload-test");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("FileController");
        endpoint.setMethodName("uploadOptional");
        endpoint.setPath("/api/v1/optional-file");
        endpoint.setHttpMethod("POST");
        
        // Add optional file parameter
        ApiEndpoint.Parameter fileParam = new ApiEndpoint.Parameter();
        fileParam.setName("file");
        fileParam.setType("MultipartFile");
        fileParam.setIn("formData");
        fileParam.setRequired(false); // Optional file upload
        endpoint.getParameters().add(fileParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        JsonNode operation = specNode.get("paths").get("/api/v1/optional-file").get("post");
        JsonNode requestBody = operation.get("requestBody");
        
        // Request body should not be required when all file parameters are optional
        assertThat(requestBody.get("required").asBoolean()).isFalse();
        
        // The file property should not be in the required array
        JsonNode schema = requestBody.get("content").get("multipart/form-data").get("schema");
        JsonNode required = schema.get("required");
        if (required != null) {
            assertThat(required).isEmpty();
        }
    }
    
    @Test
    void testSwaggerUIFileUploadRendering() throws Exception {
        // This test verifies that the OpenAPI specification includes all necessary elements
        // for proper file upload rendering in Swagger UI and other OpenAPI viewers
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("swagger-ui-test");
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setControllerClass("FileController");
        endpoint.setMethodName("upload");
        endpoint.setPath("/api/v1/upload");
        endpoint.setHttpMethod("POST");
        
        // Add file parameter
        ApiEndpoint.Parameter fileParam = new ApiEndpoint.Parameter();
        fileParam.setName("document");
        fileParam.setType("MultipartFile");
        fileParam.setIn("formData");
        fileParam.setRequired(true);
        endpoint.getParameters().add(fileParam);
        
        scanResult.getEndpoints().add(endpoint);
        
        // Generate OpenAPI specification
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Parse the YAML to verify structure
        JsonNode specNode = yamlMapper.readTree(yamlSpec);
        
        JsonNode operation = specNode.get("paths").get("/api/v1/upload").get("post");
        JsonNode requestBody = operation.get("requestBody");
        JsonNode multipartContent = requestBody.get("content").get("multipart/form-data");
        
        // Verify all required elements for proper Swagger UI file upload rendering
        
        // 1. Correct content type
        assertThat(multipartContent).isNotNull();
        
        // 2. Object schema with file property
        JsonNode schema = multipartContent.get("schema");
        assertThat(schema.get("type").asText()).isEqualTo("object");
        
        JsonNode documentProperty = schema.get("properties").get("document");
        assertThat(documentProperty.get("type").asText()).isEqualTo("string");
        assertThat(documentProperty.get("format").asText()).isEqualTo("binary");
        
        // 3. Required field specification
        JsonNode required = schema.get("required");
        assertThat(required).contains(yamlMapper.valueToTree("document"));
        
        // 4. Encoding information for enhanced tool support
        JsonNode encoding = multipartContent.get("encoding");
        assertThat(encoding).isNotNull();
        JsonNode documentEncoding = encoding.get("document");
        assertThat(documentEncoding).isNotNull();
        assertThat(documentEncoding.get("contentType").asText()).isEqualTo("application/octet-stream");
        
        // 5. Request body is marked as required
        assertThat(requestBody.get("required").asBoolean()).isTrue();
        
        System.out.println("✅ VERIFIED: All elements for proper Swagger UI file upload rendering are present");
        System.out.println("✅ FIXED: OpenAPI specification now includes encoding information");
        System.out.println("✅ IMPROVEMENT: File upload controls should render correctly in OpenAPI viewers");
    }
}