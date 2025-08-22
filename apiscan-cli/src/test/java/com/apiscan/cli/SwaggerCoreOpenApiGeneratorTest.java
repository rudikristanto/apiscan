package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for SwaggerCoreOpenApiGenerator to ensure enterprise-grade OpenAPI specification generation.
 * These tests validate compliance with OpenAPI 3.0.3 specification and proper handling of all API patterns.
 */
class SwaggerCoreOpenApiGeneratorTest {

    private SwaggerCoreOpenApiGenerator generator;
    private ObjectMapper yamlMapper;
    private ObjectMapper jsonMapper;

    @BeforeEach
    void setUp() {
        generator = new SwaggerCoreOpenApiGenerator();
        yamlMapper = new ObjectMapper(new YAMLFactory());
        jsonMapper = new ObjectMapper();
    }

    @Test
    void shouldGenerateBasicOpenApiSpecification() throws Exception {
        // Given
        ScanResult scanResult = createBasicScanResult();
        
        // When
        String yamlSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        String jsonSpec = generator.generate(scanResult, ApiScanCLI.OutputFormat.json);
        
        // Then
        assertThat(yamlSpec).isNotEmpty();
        assertThat(jsonSpec).isNotEmpty();
        
        // Validate YAML parsing
        JsonNode yamlNode = yamlMapper.readTree(yamlSpec);
        assertThat(yamlNode.get("openapi").asText()).isEqualTo("3.0.3");
        assertThat(yamlNode.get("info").get("title").asText()).isEqualTo("API Documentation");
        assertThat(yamlNode.get("info").get("version").asText()).isEqualTo("1.0.0");
        
        // Validate JSON parsing  
        JsonNode jsonNode = jsonMapper.readTree(jsonSpec);
        assertThat(jsonNode.get("openapi").asText()).isEqualTo("3.0.3");
        assertThat(jsonNode.get("info").get("title").asText()).isEqualTo("API Documentation");
    }

    @Test
    void shouldHandlePathParametersCorrectly() throws Exception {
        // Given
        ScanResult scanResult = createScanResultWithPathParameters();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        JsonNode pathsNode = node.get("paths");
        
        // Validate /api/owners/{ownerId}
        JsonNode ownerPath = pathsNode.get("/api/owners/{ownerId}");
        assertThat(ownerPath).isNotNull();
        
        JsonNode getOperation = ownerPath.get("get");
        JsonNode parameters = getOperation.get("parameters");
        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters).hasSize(1);
        
        JsonNode ownerIdParam = parameters.get(0);
        assertThat(ownerIdParam.get("name").asText()).isEqualTo("ownerId");
        assertThat(ownerIdParam.get("in").asText()).isEqualTo("path");
        assertThat(ownerIdParam.get("required").asBoolean()).isTrue();
        assertThat(ownerIdParam.get("schema").get("type").asText()).isEqualTo("integer");
    }

    @Test
    void shouldHandlePathParameterVariations() throws Exception {
        // Given - Test the vetId/id parameter mapping issue from the original file
        ScanResult scanResult = createScanResultWithParameterVariations();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        JsonNode pathsNode = node.get("paths");
        
        // Validate /api/vet/{id} with vetId parameter
        JsonNode vetPath = pathsNode.get("/api/vet/{id}");
        assertThat(vetPath).isNotNull();
        
        JsonNode putOperation = vetPath.get("put");
        JsonNode parameters = putOperation.get("parameters");
        assertThat(parameters.isArray()).isTrue();
        assertThat(parameters).hasSize(2); // Should have vetId query param + id path param
        
        // Check that we have both the vetId query parameter and the id path parameter
        boolean hasVetIdQuery = false;
        boolean hasIdPath = false;
        
        for (JsonNode param : parameters) {
            String name = param.get("name").asText();
            String in = param.get("in").asText();
            
            if ("vetId".equals(name) && "query".equals(in)) {
                hasVetIdQuery = true;
                assertThat(param.get("required").asBoolean()).isFalse();
            } else if ("id".equals(name) && "path".equals(in)) {
                hasIdPath = true;
                assertThat(param.get("required").asBoolean()).isTrue();
                assertThat(param.get("schema").get("type").asText()).isEqualTo("string"); // Default type for generated path params
            }
        }
        
        assertThat(hasVetIdQuery).isTrue(); // vetId should be preserved as query parameter
        assertThat(hasIdPath).isTrue(); // id should be auto-generated as path parameter
    }

    @Test
    void shouldNormalizePathsCorrectly() throws Exception {
        // Given
        ScanResult scanResult = createScanResultWithVariousPathFormats();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        JsonNode pathsNode = node.get("paths");
        
        // All paths should start with /
        assertThat(pathsNode.get("/")).isNotNull(); // Root path
        assertThat(pathsNode.get("/api/pets")).isNotNull(); // Path without leading /
        assertThat(pathsNode.get("/api/owners")).isNotNull(); // Path with leading /
        
        // Paths without leading / should not exist in the spec
        assertThat(pathsNode.get("api/pets")).isNull();
    }

    @Test
    void shouldGenerateValidResponsesForAllScenarios() throws Exception {
        // Given
        ScanResult scanResult = createScanResultWithVariousResponseTypes();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        JsonNode pathsNode = node.get("paths");
        
        // Check array response
        JsonNode listEndpoint = pathsNode.get("/api/owners").get("get");
        JsonNode listResponse = listEndpoint.get("responses").get("200");
        assertThat(listResponse.get("description").asText()).isEqualTo("List of owners");
        JsonNode arraySchema = listResponse.get("content").get("application/json").get("schema");
        assertThat(arraySchema.get("type").asText()).isEqualTo("array");
        JsonNode itemsSchema = arraySchema.get("items");
        // Items can either be inline objects or references to DTO schemas
        assertThat(itemsSchema).isNotNull();
        boolean hasType = itemsSchema.has("type");
        boolean hasRef = itemsSchema.has("$ref");
        assertThat(hasType || hasRef).isTrue();
        
        // Check object response  
        JsonNode getEndpoint = pathsNode.get("/api/owners/{ownerId}").get("get");
        JsonNode objectResponse = getEndpoint.get("responses").get("200");
        assertThat(objectResponse.get("description").asText()).isEqualTo("Owner details");
        JsonNode objectSchema = objectResponse.get("content").get("application/json").get("schema");
        // Schema can either be inline object or reference to DTO schema
        boolean objectHasType = objectSchema.has("type") && objectSchema.get("type").asText().equals("object");
        boolean objectHasRef = objectSchema.has("$ref");
        assertThat(objectHasType || objectHasRef).isTrue();
        
        // Check default response for endpoints without explicit responses
        JsonNode defaultEndpoint = pathsNode.get("/").get("get");
        JsonNode defaultResponse = defaultEndpoint.get("responses").get("200");
        assertThat(defaultResponse.get("description").asText()).isEqualTo("List of resources returned successfully.");
    }

    @Test
    void shouldHandleTagsCorrectly() throws Exception {
        // Given
        ScanResult scanResult = createScanResultWithTags();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        
        // Validate tags section
        JsonNode tags = node.get("tags");
        assertThat(tags.isArray()).isTrue();
        assertThat(tags).hasSize(2);
        
        List<String> tagNames = new ArrayList<>();
        for (JsonNode tag : tags) {
            tagNames.add(tag.get("name").asText());
            assertThat(tag.get("description").asText()).endsWith(" operations");
        }
        Collections.sort(tagNames);
        assertThat(tagNames).containsExactly("Owner", "Pet");
    }

    @Test
    void shouldGenerateValidOpenApi303Specification() throws Exception {
        // Given - Complex scan result that covers all major patterns
        ScanResult scanResult = createComprehensiveScanResult();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then - Validate OpenAPI 3.0.3 compliance
        JsonNode node = yamlMapper.readTree(spec);
        
        // Required OpenAPI 3.0.3 fields
        assertThat(node.get("openapi").asText()).isEqualTo("3.0.3");
        assertThat(node.get("info")).isNotNull();
        assertThat(node.get("paths")).isNotNull();
        
        // Info object validation
        JsonNode info = node.get("info");
        assertThat(info.get("title")).isNotNull();
        assertThat(info.get("version")).isNotNull();
        assertThat(info.get("description")).isNotNull();
        
        // No null values in the specification
        String specString = spec.toLowerCase();
        assertThat(specString).doesNotContain("null");
        assertThat(specString).doesNotContain("examplesetflag");
        
        // Validate paths structure
        JsonNode paths = node.get("paths");
        for (Iterator<String> pathIterator = paths.fieldNames(); pathIterator.hasNext(); ) {
            String pathKey = pathIterator.next();
            assertThat(pathKey).startsWith("/");
            
            JsonNode pathItem = paths.get(pathKey);
            for (Iterator<String> methodIterator = pathItem.fieldNames(); methodIterator.hasNext(); ) {
                String method = methodIterator.next();
                if (Arrays.asList("get", "post", "put", "delete", "patch", "head", "options").contains(method)) {
                    JsonNode operation = pathItem.get(method);
                    assertThat(operation.get("responses")).isNotNull();
                    assertThat(operation.get("operationId")).isNotNull();
                }
            }
        }
    }

    @Test
    void shouldSaveToFile(@TempDir Path tempDir) throws Exception {
        // Given
        ScanResult scanResult = createBasicScanResult();
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        Path outputFile = tempDir.resolve("test-openapi.yaml");
        
        // When
        generator.saveToFile(spec, outputFile);
        
        // Then
        assertThat(Files.exists(outputFile)).isTrue();
        String fileContent = Files.readString(outputFile);
        assertThat(fileContent).isEqualTo(spec);
        
        // Validate the saved file is valid YAML
        JsonNode node = yamlMapper.readTree(fileContent);
        assertThat(node.get("openapi").asText()).isEqualTo("3.0.3");
    }

    @Test  
    void shouldEnsureOpenApiValidationCompliance() throws Exception {
        // Given - Create endpoint with mismatched parameter names (the problematic scenario)
        ScanResult scanResult = createScanResultWithMismatchedParameterNames();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then - Validate OpenAPI compliance rules are followed
        JsonNode node = yamlMapper.readTree(spec);
        JsonNode pathsNode = node.get("paths");
        
        // Test Case 1: /api/vet/{id} should have parameter named "id", not "vetId"
        JsonNode vetIdPath = pathsNode.get("/api/vet/{id}");
        assertThat(vetIdPath).isNotNull();
        
        JsonNode putOp = vetIdPath.get("put");
        if (putOp != null && putOp.has("parameters")) {
            JsonNode params = putOp.get("parameters");
            assertThat(params.isArray()).isTrue();
            
            // Find path parameter
            boolean foundValidPathParam = false;
            for (JsonNode param : params) {
                if ("path".equals(param.get("in").asText())) {
                    assertThat(param.get("name").asText()).isEqualTo("id"); // Must match path segment exactly
                    assertThat(param.get("required").asBoolean()).isTrue();
                    foundValidPathParam = true;
                }
            }
            assertThat(foundValidPathParam).isTrue();
        }
        
        // Test Case 2: /api/owners/{ownerId} should have parameter named "ownerId" 
        JsonNode ownerPath = pathsNode.get("/api/owners/{ownerId}");
        assertThat(ownerPath).isNotNull();
        
        JsonNode getOp = ownerPath.get("get");
        if (getOp != null && getOp.has("parameters")) {
            JsonNode params = getOp.get("parameters");
            
            boolean foundValidOwnerParam = false;
            for (JsonNode param : params) {
                if ("path".equals(param.get("in").asText())) {
                    assertThat(param.get("name").asText()).isEqualTo("ownerId"); // Exact match required
                    foundValidOwnerParam = true;
                }
            }
            assertThat(foundValidOwnerParam).isTrue();
        }
        
        // Test Case 3: Ensure no orphaned parameters (parameters without matching path segments)
        for (Iterator<String> pathIterator = pathsNode.fieldNames(); pathIterator.hasNext(); ) {
            String pathKey = pathIterator.next();
            JsonNode pathItem = pathsNode.get(pathKey);
            
            // Extract path parameter names from URL  
            Set<String> pathSegmentParams = new HashSet<>();
            for (String segment : pathKey.split("/")) {
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    pathSegmentParams.add(segment.substring(1, segment.length() - 1));
                }
            }
            
            // Check all operations
            for (Iterator<String> methodIterator = pathItem.fieldNames(); methodIterator.hasNext(); ) {
                String method = methodIterator.next();
                JsonNode operation = pathItem.get(method);
                
                if (operation.has("parameters")) {
                    for (JsonNode param : operation.get("parameters")) {
                        if ("path".equals(param.get("in").asText())) {
                            String paramName = param.get("name").asText();
                            assertThat(pathSegmentParams)
                                .as("Path parameter '%s' in %s %s must have corresponding {%s} in path", 
                                    paramName, method.toUpperCase(), pathKey, paramName)
                                .contains(paramName);
                        }
                    }
                }
            }
        }
    }

    private ScanResult createBasicScanResult() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/test");
        endpoint.setHttpMethod("GET");
        endpoint.setOperationId("TestController_getTest");
        endpoint.setControllerClass("TestController");
        
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithPathParameters() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/owners/{ownerId}");
        endpoint.setHttpMethod("GET");
        endpoint.setOperationId("OwnerController_getOwner");
        endpoint.setControllerClass("OwnerController");
        
        // Add path parameter
        ApiEndpoint.Parameter param = new ApiEndpoint.Parameter();
        param.setName("ownerId");
        param.setIn("query"); // This will be corrected to "path" by the generator
        param.setType("Integer");
        param.setRequired(false); // This will be corrected to true for path params
        endpoint.getParameters().add(param);
        
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithParameterVariations() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        // Test the problematic vetId/id mapping
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/vet/{id}");
        endpoint.setHttpMethod("PUT");
        endpoint.setOperationId("VetController_updateVet");
        endpoint.setControllerClass("VetController");
        
        // Parameter name is vetId but path uses {id}
        ApiEndpoint.Parameter param = new ApiEndpoint.Parameter();
        param.setName("vetId");
        param.setIn("query"); // Should be detected as path parameter
        param.setType("Integer");
        param.setRequired(false);
        endpoint.getParameters().add(param);
        
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithVariousPathFormats() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        // Path with leading /
        ApiEndpoint endpoint1 = new ApiEndpoint();
        endpoint1.setPath("/api/owners");
        endpoint1.setHttpMethod("GET");
        endpoint1.setOperationId("OwnerController_list");
        endpoint1.setControllerClass("OwnerController");
        scanResult.addEndpoint(endpoint1);
        
        // Path without leading /
        ApiEndpoint endpoint2 = new ApiEndpoint();
        endpoint2.setPath("api/pets");
        endpoint2.setHttpMethod("GET");
        endpoint2.setOperationId("PetController_list");
        endpoint2.setControllerClass("PetController");
        scanResult.addEndpoint(endpoint2);
        
        // Root path
        ApiEndpoint endpoint3 = new ApiEndpoint();
        endpoint3.setPath("/");
        endpoint3.setHttpMethod("GET");
        endpoint3.setOperationId("RootController_redirect");
        endpoint3.setControllerClass("RootController");
        scanResult.addEndpoint(endpoint3);
        
        return scanResult;
    }

    private ScanResult createScanResultWithVariousResponseTypes() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        // Endpoint with array response
        ApiEndpoint listEndpoint = new ApiEndpoint();
        listEndpoint.setPath("/api/owners");
        listEndpoint.setHttpMethod("GET");
        listEndpoint.setOperationId("OwnerController_list");
        listEndpoint.setControllerClass("OwnerController");
        
        ApiEndpoint.Response listResponse = new ApiEndpoint.Response();
        listResponse.setDescription("List of owners");
        ApiEndpoint.MediaType listMedia = new ApiEndpoint.MediaType();
        listMedia.setSchema("List<OwnerDto>");
        listResponse.getContent().put("application/json", listMedia);
        listEndpoint.getResponses().put("200", listResponse);
        
        scanResult.addEndpoint(listEndpoint);
        
        // Endpoint with object response
        ApiEndpoint getEndpoint = new ApiEndpoint();
        getEndpoint.setPath("/api/owners/{ownerId}");
        getEndpoint.setHttpMethod("GET");
        getEndpoint.setOperationId("OwnerController_get");
        getEndpoint.setControllerClass("OwnerController");
        
        ApiEndpoint.Parameter param = new ApiEndpoint.Parameter();
        param.setName("ownerId");
        param.setType("Integer");
        getEndpoint.getParameters().add(param);
        
        ApiEndpoint.Response getResponse = new ApiEndpoint.Response();
        getResponse.setDescription("Owner details");
        ApiEndpoint.MediaType getMedia = new ApiEndpoint.MediaType();
        getMedia.setSchema("OwnerDto");
        getResponse.getContent().put("application/json", getMedia);
        getEndpoint.getResponses().put("200", getResponse);
        
        scanResult.addEndpoint(getEndpoint);
        
        // Endpoint without explicit response (should get default)
        ApiEndpoint defaultEndpoint = new ApiEndpoint();
        defaultEndpoint.setPath("/");
        defaultEndpoint.setHttpMethod("GET");
        defaultEndpoint.setOperationId("RootController_redirect");
        defaultEndpoint.setControllerClass("RootController");
        
        scanResult.addEndpoint(defaultEndpoint);
        
        return scanResult;
    }

    private ScanResult createScanResultWithTags() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        // Endpoint with explicit tags
        ApiEndpoint endpoint1 = new ApiEndpoint();
        endpoint1.setPath("/api/owners");
        endpoint1.setHttpMethod("GET");
        endpoint1.setOperationId("OwnerController_list");
        endpoint1.setControllerClass("OwnerController");
        endpoint1.setTags(Arrays.asList("Owner"));
        scanResult.addEndpoint(endpoint1);
        
        // Endpoint with controller-derived tags
        ApiEndpoint endpoint2 = new ApiEndpoint();
        endpoint2.setPath("/api/pets");
        endpoint2.setHttpMethod("GET");
        endpoint2.setOperationId("PetRestController_list");
        endpoint2.setControllerClass("PetRestController");
        // Tags will be derived from controller class name
        scanResult.addEndpoint(endpoint2);
        
        return scanResult;
    }

    private ScanResult createComprehensiveScanResult() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(2000);
        
        // Complex endpoint with all features
        ApiEndpoint complexEndpoint = new ApiEndpoint();
        complexEndpoint.setPath("/api/owners/{ownerId}/pets/{petId}");
        complexEndpoint.setHttpMethod("PUT");
        complexEndpoint.setOperationId("OwnerController_updatePet");
        complexEndpoint.setControllerClass("OwnerRestController");
        complexEndpoint.setSummary("Update pet for owner");
        complexEndpoint.setDescription("Updates a specific pet for a given owner");
        complexEndpoint.setDeprecated(false);
        
        // Path parameters
        ApiEndpoint.Parameter ownerIdParam = new ApiEndpoint.Parameter();
        ownerIdParam.setName("ownerId");
        ownerIdParam.setType("Integer");
        ownerIdParam.setDescription("Owner identifier");
        complexEndpoint.getParameters().add(ownerIdParam);
        
        ApiEndpoint.Parameter petIdParam = new ApiEndpoint.Parameter();
        petIdParam.setName("petId");
        petIdParam.setType("Integer");
        petIdParam.setDescription("Pet identifier");
        complexEndpoint.getParameters().add(petIdParam);
        
        // Query parameter
        ApiEndpoint.Parameter queryParam = new ApiEndpoint.Parameter();
        queryParam.setName("validate");
        queryParam.setIn("query");
        queryParam.setType("Boolean");
        queryParam.setRequired(false);
        queryParam.setDescription("Whether to validate before update");
        complexEndpoint.getParameters().add(queryParam);
        
        // Request body
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        requestBody.setDescription("Pet data to update");
        requestBody.setRequired(true);
        ApiEndpoint.MediaType requestMedia = new ApiEndpoint.MediaType();
        requestMedia.setSchema("PetUpdateDto");
        requestBody.getContent().put("application/json", requestMedia);
        complexEndpoint.setRequestBody(requestBody);
        
        // Response
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        response.setDescription("Updated pet details");
        ApiEndpoint.MediaType responseMedia = new ApiEndpoint.MediaType();
        responseMedia.setSchema("PetDto");
        response.getContent().put("application/json", responseMedia);
        complexEndpoint.getResponses().put("200", response);
        
        // Error response
        ApiEndpoint.Response errorResponse = new ApiEndpoint.Response();
        errorResponse.setDescription("Pet not found");
        complexEndpoint.getResponses().put("404", errorResponse);
        
        scanResult.addEndpoint(complexEndpoint);
        
        // Add a few more endpoints for completeness
        scanResult.addEndpoint(createBasicScanResult().getEndpoints().get(0));
        
        return scanResult;
    }

    private ScanResult createScanResultWithMismatchedParameterNames() {
        ScanResult scanResult = new ScanResult();
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        // Create endpoint with mismatched parameter name (the exact problem from validation errors)
        ApiEndpoint vetEndpoint = new ApiEndpoint();
        vetEndpoint.setPath("/api/vet/{id}"); // Path has {id}
        vetEndpoint.setHttpMethod("PUT");
        vetEndpoint.setOperationId("VetController_updateVet");
        vetEndpoint.setControllerClass("VetController");
        
        // Parameter name is vetId but path uses {id} - this should be corrected
        ApiEndpoint.Parameter vetIdParam = new ApiEndpoint.Parameter();
        vetIdParam.setName("vetId"); // Mismatched name!
        vetIdParam.setIn("query");
        vetIdParam.setType("Integer");
        vetIdParam.setRequired(false);
        vetEndpoint.getParameters().add(vetIdParam);
        
        scanResult.addEndpoint(vetEndpoint);
        
        // Add a correctly matched endpoint for comparison
        ApiEndpoint ownerEndpoint = new ApiEndpoint();
        ownerEndpoint.setPath("/api/owners/{ownerId}"); // Path has {ownerId}
        ownerEndpoint.setHttpMethod("GET");
        ownerEndpoint.setOperationId("OwnerController_getOwner");
        ownerEndpoint.setControllerClass("OwnerController");
        
        // Correctly matched parameter
        ApiEndpoint.Parameter ownerIdParam = new ApiEndpoint.Parameter();
        ownerIdParam.setName("ownerId"); // Matches path exactly
        ownerIdParam.setIn("query");
        ownerIdParam.setType("Integer");
        ownerIdParam.setRequired(false);
        ownerEndpoint.getParameters().add(ownerIdParam);
        
        scanResult.addEndpoint(ownerEndpoint);
        
        return scanResult;
    }

    @Test
    void shouldGenerateSchemaForRequestBodyDto(@TempDir Path tempDir) throws Exception {
        // Given - Create a mock DTO file
        Path dtoDir = tempDir.resolve("target/generated-sources/openapi/src/main/java/com/example");
        Files.createDirectories(dtoDir);
        
        Path ownerDtoFile = dtoDir.resolve("OwnerFieldsDto.java");
        Files.writeString(ownerDtoFile, createMockOwnerFieldsDto());
        
        // Create scan result with request body
        ScanResult scanResult = createScanResultWithRequestBody(tempDir.toString());
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        
        // Check components section exists
        JsonNode components = node.get("components");
        assertThat(components).isNotNull();
        
        JsonNode schemas = components.get("schemas");
        assertThat(schemas).isNotNull();
        
        // Check OwnerFieldsDto schema was generated
        JsonNode ownerSchema = schemas.get("OwnerFieldsDto");
        assertThat(ownerSchema).isNotNull();
        assertThat(ownerSchema.get("type").asText()).isEqualTo("object");
        
        // Check request body references the schema
        JsonNode postOp = node.get("paths").get("/api/owners").get("post");
        JsonNode requestBody = postOp.get("requestBody");
        assertThat(requestBody).isNotNull();
        
        JsonNode schema = requestBody.get("content").get("application/json").get("schema");
        assertThat(schema.get("$ref").asText()).isEqualTo("#/components/schemas/OwnerFieldsDto");
    }

    @Test
    void shouldGenerateSchemaForResponseDto(@TempDir Path tempDir) throws Exception {
        // Given - Create mock DTO files
        Path dtoDir = tempDir.resolve("target/generated-sources/openapi/src/main/java/com/example");
        Files.createDirectories(dtoDir);
        
        Files.writeString(dtoDir.resolve("OwnerDto.java"), createMockOwnerDto());
        
        // Create scan result with response
        ScanResult scanResult = createScanResultWithResponse(tempDir.toString());
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        
        // Check OwnerDto schema was generated
        JsonNode schemas = node.get("components").get("schemas");
        JsonNode ownerSchema = schemas.get("OwnerDto");
        assertThat(ownerSchema).isNotNull();
        
        // Check response references the schema
        JsonNode getOp = node.get("paths").get("/api/owners/{ownerId}").get("get");
        JsonNode response = getOp.get("responses").get("200");
        JsonNode responseSchema = response.get("content").get("application/json").get("schema");
        assertThat(responseSchema.get("$ref").asText()).isEqualTo("#/components/schemas/OwnerDto");
    }

    @Test
    void shouldGeneratePlaceholderForMissingDto() throws Exception {
        // Given - Scan result with DTO that doesn't exist
        ScanResult scanResult = createScanResultWithMissingDto();
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        
        // Check placeholder schema was created
        JsonNode schemas = node.get("components").get("schemas");
        JsonNode missingSchema = schemas.get("MissingDto");
        assertThat(missingSchema).isNotNull();
        assertThat(missingSchema.get("description").asText()).contains("Schema not available");
        assertThat(missingSchema.get("properties").get("_schemaPlaceholder")).isNotNull();
    }

    @Test
    void shouldHandleArrayResponsesWithDtoItems(@TempDir Path tempDir) throws Exception {
        // Given - Create mock DTO 
        Path dtoDir = tempDir.resolve("target/generated-sources/openapi/src/main/java/com/example");
        Files.createDirectories(dtoDir);
        Files.writeString(dtoDir.resolve("OwnerDto.java"), createMockOwnerDto());
        
        // Create scan result with List<OwnerDto> response
        ScanResult scanResult = createScanResultWithListResponse(tempDir.toString());
        
        // When
        String spec = generator.generate(scanResult, ApiScanCLI.OutputFormat.yaml);
        
        // Then
        JsonNode node = yamlMapper.readTree(spec);
        
        // Check array response with DTO items
        JsonNode getOp = node.get("paths").get("/api/owners").get("get");
        JsonNode responseSchema = getOp.get("responses").get("200").get("content").get("application/json").get("schema");
        
        assertThat(responseSchema.get("type").asText()).isEqualTo("array");
        assertThat(responseSchema.get("items").get("$ref").asText()).isEqualTo("#/components/schemas/OwnerDto");
        
        // Check OwnerDto schema exists in components
        JsonNode ownerSchema = node.get("components").get("schemas").get("OwnerDto");
        assertThat(ownerSchema).isNotNull();
    }

    private ScanResult createScanResultWithRequestBody(String projectPath) {
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath(projectPath);
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/owners");
        endpoint.setHttpMethod("POST");
        endpoint.setOperationId("OwnerController_addOwner");
        endpoint.setControllerClass("OwnerController");
        
        // Add request body with DTO
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        requestBody.setDescription("Owner data");
        requestBody.setRequired(true);
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("OwnerFieldsDto");
        requestBody.getContent().put("application/json", mediaType);
        
        endpoint.setRequestBody(requestBody);
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithResponse(String projectPath) {
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath(projectPath);
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/owners/{ownerId}");
        endpoint.setHttpMethod("GET");
        endpoint.setOperationId("OwnerController_getOwner");
        endpoint.setControllerClass("OwnerController");
        
        // Add response with DTO
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        response.setDescription("Owner details");
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("OwnerDto");
        response.getContent().put("application/json", mediaType);
        
        endpoint.getResponses().put("200", response);
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithMissingDto() {
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath("/nonexistent/path");
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/missing");
        endpoint.setHttpMethod("POST");
        endpoint.setOperationId("TestController_missing");
        endpoint.setControllerClass("TestController");
        
        // Add request body with missing DTO
        ApiEndpoint.RequestBody requestBody = new ApiEndpoint.RequestBody();
        requestBody.setRequired(true);
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("MissingDto");
        requestBody.getContent().put("application/json", mediaType);
        
        endpoint.setRequestBody(requestBody);
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private ScanResult createScanResultWithListResponse(String projectPath) {
        ScanResult scanResult = new ScanResult();
        scanResult.setProjectPath(projectPath);
        scanResult.setFramework("Spring");
        scanResult.setScanDurationMs(1000);
        
        ApiEndpoint endpoint = new ApiEndpoint();
        endpoint.setPath("/api/owners");
        endpoint.setHttpMethod("GET");
        endpoint.setOperationId("OwnerController_listOwners");
        endpoint.setControllerClass("OwnerController");
        
        // Add response with List<OwnerDto>
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        response.setDescription("List of owners");
        
        ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
        mediaType.setSchema("List<OwnerDto>");
        response.getContent().put("application/json", mediaType);
        
        endpoint.getResponses().put("200", response);
        scanResult.addEndpoint(endpoint);
        return scanResult;
    }

    private String createMockOwnerFieldsDto() {
        return """
            package com.example;
            
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Size;
            import io.swagger.v3.oas.annotations.media.Schema;
            
            @Schema(name = "OwnerFields", description = "Owner editable fields")
            public class OwnerFieldsDto {
                
                @NotNull
                @Size(min = 1, max = 30)
                private String firstName;
                
                @NotNull
                @Size(min = 1, max = 30)
                private String lastName;
                
                @NotNull
                @Size(min = 1, max = 255)
                private String address;
                
                @NotNull
                @Size(min = 1, max = 80)
                private String city;
                
                @NotNull
                @Size(min = 1, max = 20)
                private String telephone;
                
                // Getters and setters
                public String getFirstName() { return firstName; }
                public void setFirstName(String firstName) { this.firstName = firstName; }
                
                public String getLastName() { return lastName; }
                public void setLastName(String lastName) { this.lastName = lastName; }
                
                public String getAddress() { return address; }
                public void setAddress(String address) { this.address = address; }
                
                public String getCity() { return city; }
                public void setCity(String city) { this.city = city; }
                
                public String getTelephone() { return telephone; }
                public void setTelephone(String telephone) { this.telephone = telephone; }
            }
            """;
    }

    private String createMockOwnerDto() {
        return """
            package com.example;
            
            import io.swagger.v3.oas.annotations.media.Schema;
            import java.util.List;
            
            @Schema(name = "Owner", description = "Complete owner representation")
            public class OwnerDto {
                private Integer id;
                private String firstName;
                private String lastName;
                private String address;
                private String city;
                private String telephone;
                private List<String> pets;
                
                // Getters and setters
                public Integer getId() { return id; }
                public void setId(Integer id) { this.id = id; }
                
                public String getFirstName() { return firstName; }
                public void setFirstName(String firstName) { this.firstName = firstName; }
                
                public String getLastName() { return lastName; }
                public void setLastName(String lastName) { this.lastName = lastName; }
                
                public String getAddress() { return address; }
                public void setAddress(String address) { this.address = address; }
                
                public String getCity() { return city; }
                public void setCity(String city) { this.city = city; }
                
                public String getTelephone() { return telephone; }
                public void setTelephone(String telephone) { this.telephone = telephone; }
                
                public List<String> getPets() { return pets; }
                public void setPets(List<String> pets) { this.pets = pets; }
            }
            """;
    }
    
    @Test
    void testEnhancedOpenApiFeatures() throws Exception {
        ScanResult result = createScanResultWithPathParameters();
        SwaggerCoreOpenApiGenerator generator = new SwaggerCoreOpenApiGenerator();
        
        String yaml = generator.generate(result, ApiScanCLI.OutputFormat.yaml);
        assertThat(yaml).isNotEmpty();
        
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        JsonNode rootNode = yamlMapper.readTree(yaml);
        
        // Test auto-generated summaries and descriptions
        JsonNode pathsNode = rootNode.get("paths");
        assertThat(pathsNode).isNotNull();
        
        // Test GET /api/owners (list operation)
        JsonNode ownersPath = pathsNode.get("/api/owners");
        if (ownersPath != null && ownersPath.has("get")) {
            JsonNode getOperation = ownersPath.get("get");
            assertThat(getOperation.get("summary").asText()).isEqualTo("List owners");
            assertThat(getOperation.get("description").asText()).isEqualTo("Returns an array of owners.");
        }
        
        // Test GET /api/owners/{ownerId} (single resource operation)
        JsonNode ownerByIdPath = pathsNode.get("/api/owners/{ownerId}");
        if (ownerByIdPath != null && ownerByIdPath.has("get")) {
            JsonNode getOperation = ownerByIdPath.get("get");
            assertThat(getOperation.get("summary").asText()).isEqualTo("Get a owner by ID");
            assertThat(getOperation.get("description").asText()).isEqualTo("Returns the owner or a 404 error.");
        }
        
        // Test POST operation
        if (ownersPath != null && ownersPath.has("post")) {
            JsonNode postOperation = ownersPath.get("post");
            assertThat(postOperation.get("summary").asText()).isEqualTo("Create a owner");
            assertThat(postOperation.get("description").asText()).isEqualTo("Creates a owner.");
        }
        
        // Test multiple response codes
        if (ownerByIdPath != null && ownerByIdPath.has("get")) {
            JsonNode getOperation = ownerByIdPath.get("get");
            JsonNode responses = getOperation.get("responses");
            
            // Should have multiple response codes
            assertThat(responses.has("200")).isTrue();
            assertThat(responses.has("400")).isTrue();
            assertThat(responses.has("404")).isTrue();
            assertThat(responses.has("500")).isTrue();
            
            // Check response descriptions
            assertThat(responses.get("200").get("description").asText()).isEqualTo("owner details found and returned.");
            assertThat(responses.get("404").get("description").asText()).isEqualTo("owner not found.");
        }
        
        // Test parameter descriptions and constraints
        if (ownerByIdPath != null && ownerByIdPath.has("get")) {
            JsonNode getOperation = ownerByIdPath.get("get");
            if (getOperation.has("parameters")) {
                JsonNode parameters = getOperation.get("parameters");
                for (JsonNode param : parameters) {
                    if ("path".equals(param.get("in").asText()) && "ownerId".equals(param.get("name").asText())) {
                        assertThat(param.get("description").asText()).isEqualTo("The ID of the owner.");
                        JsonNode schema = param.get("schema");
                        assertThat(schema.get("type").asText()).isEqualTo("integer");
                        assertThat(schema.get("format").asText()).isEqualTo("int32");
                        assertThat(schema.get("minimum").asInt()).isEqualTo(0);
                    }
                }
            }
        }
        
        // Test POST response code (should be 201)
        if (ownersPath != null && ownersPath.has("post")) {
            JsonNode postOperation = ownersPath.get("post");
            JsonNode responses = postOperation.get("responses");
            assertThat(responses.has("201")).isTrue();
            assertThat(responses.get("201").get("description").asText()).isEqualTo("Resource created successfully.");
        }
    }
}