package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAPI generator using Swagger Core library for enterprise-grade specification generation.
 * This replaces the custom implementation to ensure full OpenAPI 3.0.3 compliance.
 */
public class SwaggerCoreOpenApiGenerator {
    
    private final DtoSchemaResolver dtoSchemaResolver;
    
    public SwaggerCoreOpenApiGenerator() {
        this.dtoSchemaResolver = null; // Will be initialized when needed
    }
    
    public String generate(ScanResult scanResult, ApiScanCLI.OutputFormat format) {
        OpenAPI openApi = buildOpenApiSpec(scanResult);
        
        try {
            if (format == ApiScanCLI.OutputFormat.json) {
                return Json.pretty(openApi);
            } else {
                return Yaml.pretty(openApi);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OpenAPI specification", e);
        }
    }
    
    public void saveToFile(String content, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, content);
    }
    
    private OpenAPI buildOpenApiSpec(ScanResult scanResult) {
        OpenAPI openApi = new OpenAPI();
        
        // Set OpenAPI version and info
        openApi.openapi("3.0.3");
        openApi.info(buildInfo(scanResult));
        
        // Add servers
        openApi.servers(buildServers());
        
        // Initialize DTO schema resolver with project path
        DtoSchemaResolver schemaResolver = new DtoSchemaResolver(scanResult.getProjectPath());
        
        // Build paths and collect DTO schemas
        Paths paths = new Paths();
        Set<String> usedTags = new HashSet<>();
        Map<String, Schema> dtoSchemas = new HashMap<>();
        
        for (ApiEndpoint endpoint : scanResult.getEndpoints()) {
            addEndpointToSpec(endpoint, paths, usedTags, schemaResolver, dtoSchemas);
        }
        
        openApi.paths(paths);
        
        // Add DTO schemas to components
        if (!dtoSchemas.isEmpty()) {
            Components components = new Components();
            components.schemas(dtoSchemas);
            openApi.components(components);
        }
        
        // Add tags (only for tags that are actually used)
        List<Tag> tags = usedTags.stream()
            .sorted()
            .map(tagName -> {
                Tag tag = new Tag();
                tag.setName(tagName);
                tag.setDescription(tagName + " operations");
                return tag;
            })
            .collect(Collectors.toList());
        
        if (!tags.isEmpty()) {
            openApi.tags(tags);
        }
        
        return openApi;
    }
    
    private Info buildInfo(ScanResult scanResult) {
        Info info = new Info();
        info.title("API Documentation");
        info.description("Auto-generated API documentation for " + scanResult.getFramework() + " application");
        info.version("1.0.0");
        return info;
    }
    
    private List<Server> buildServers() {
        List<Server> servers = new ArrayList<>();
        
        Server localServer = new Server();
        localServer.url("http://localhost:8080");
        localServer.description("Local development server");
        servers.add(localServer);
        
        return servers;
    }
    
    private void addEndpointToSpec(ApiEndpoint endpoint, Paths paths, Set<String> usedTags, 
                                  DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        String path = normalizePath(endpoint.getPath());
        PathItem pathItem = paths.get(path);
        
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.addPathItem(path, pathItem);
        }
        
        Operation operation = buildOperation(endpoint, usedTags, schemaResolver, dtoSchemas);
        
        // Set operation based on HTTP method
        switch (endpoint.getHttpMethod().toUpperCase()) {
            case "GET":
                pathItem.get(operation);
                break;
            case "POST":
                pathItem.post(operation);
                break;
            case "PUT":
                pathItem.put(operation);
                break;
            case "DELETE":
                pathItem.delete(operation);
                break;
            case "PATCH":
                pathItem.patch(operation);
                break;
            case "HEAD":
                pathItem.head(operation);
                break;
            case "OPTIONS":
                pathItem.options(operation);
                break;
        }
    }
    
    /**
     * Normalize path to ensure consistent formatting.
     * All paths should start with / for OpenAPI compliance.
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        return path;
    }
    
    private Operation buildOperation(ApiEndpoint endpoint, Set<String> usedTags, 
                                    DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        Operation operation = new Operation();
        
        // Set operation ID
        operation.operationId(endpoint.getOperationId());
        
        // Set summary and description - generate if not present
        if (endpoint.getSummary() != null && !endpoint.getSummary().trim().isEmpty()) {
            operation.summary(endpoint.getSummary());
        } else {
            // Generate summary from method and path
            operation.summary(generateSummary(endpoint));
        }
        
        if (endpoint.getDescription() != null && !endpoint.getDescription().trim().isEmpty()) {
            operation.description(endpoint.getDescription());
        } else {
            // Generate description from method and path
            operation.description(generateDescription(endpoint));
        }
        
        // Set tags
        List<String> tags = endpoint.getTags();
        if (tags.isEmpty()) {
            // Use controller class name as default tag
            String tag = endpoint.getControllerClass();
            if (tag != null) {
                tag = tag.replace("Controller", "")
                         .replace("RestController", "")
                         .replace("Rest", "");
                tags = Collections.singletonList(tag);
            }
        }
        
        if (!tags.isEmpty()) {
            operation.tags(tags);
            usedTags.addAll(tags);
        }
        
        // Set parameters - combine declared parameters with path parameters
        List<Parameter> parameters = new ArrayList<>();
        
        // First, add declared parameters that match exactly or are non-path parameters
        for (ApiEndpoint.Parameter param : endpoint.getParameters()) {
            Parameter openApiParam = buildParameter(param, endpoint.getPath());
            if (openApiParam != null) {
                parameters.add(openApiParam);
            }
        }
        
        // Then, ensure all path parameters are declared (create missing ones)
        Set<String> pathParamNames = extractPathParameterNames(endpoint.getPath());
        Set<String> declaredPathParams = endpoint.getParameters().stream()
            .filter(p -> isPathParameter(p.getName(), endpoint.getPath()))
            .map(ApiEndpoint.Parameter::getName)
            .collect(Collectors.toSet());
        
        // Add missing path parameters with default values
        for (String pathParamName : pathParamNames) {
            if (!declaredPathParams.contains(pathParamName)) {
                Parameter missingPathParam = new PathParameter();
                missingPathParam.name(pathParamName);
                missingPathParam.required(true);
                Schema<Object> schema = new Schema<>();
                schema.type("string"); // Default to string for unknown path parameters
                missingPathParam.schema(schema);
                parameters.add(missingPathParam);
            }
        }
        
        if (!parameters.isEmpty()) {
            operation.parameters(parameters);
        }
        
        // Set request body
        if (endpoint.getRequestBody() != null) {
            operation.requestBody(buildRequestBody(endpoint.getRequestBody(), schemaResolver, dtoSchemas));
        }
        
        // Set responses - include multiple status codes
        ApiResponses responses = new ApiResponses();
        if (endpoint.getResponses().isEmpty()) {
            // Add comprehensive response codes based on HTTP method
            addDefaultResponses(responses, endpoint, schemaResolver, dtoSchemas);
        } else {
            // Add explicit responses first
            for (Map.Entry<String, ApiEndpoint.Response> entry : endpoint.getResponses().entrySet()) {
                responses.addApiResponse(entry.getKey(), buildResponse(entry.getValue(), schemaResolver, dtoSchemas));
            }
            // Add missing standard error responses
            addMissingStandardResponses(responses, endpoint, schemaResolver, dtoSchemas);
        }
        operation.responses(responses);
        
        // Set deprecated flag
        if (endpoint.isDeprecated()) {
            operation.deprecated(true);
        }
        
        return operation;
    }
    
    private Parameter buildParameter(ApiEndpoint.Parameter param, String path) {
        String paramName = param.getName();
        String normalizedPath = normalizePath(path);
        boolean isPathParameter = isPathParameter(paramName, normalizedPath);
        
        // Skip parameters that should be path parameters but don't match path exactly
        // These will be handled by the missing path parameter logic
        Set<String> pathParamNames = extractPathParameterNames(normalizedPath);
        if (!pathParamNames.isEmpty() && !isPathParameter && 
            (param.getIn() == null || param.getIn().equals("query"))) {
            // This might be a misnamed path parameter - skip it
            // We'll create the correct one from the path
            return null;
        }
        
        Parameter parameter;
        
        if (isPathParameter) {
            parameter = new PathParameter();
            parameter.required(true); // Path parameters are always required
        } else {
            switch (param.getIn()) {
                case "header":
                    parameter = new HeaderParameter();
                    break;
                case "query":
                default:
                    parameter = new QueryParameter();
                    break;
            }
            parameter.required(param.isRequired());
        }
        
        parameter.name(paramName);
        
        if (param.getDescription() != null && !param.getDescription().trim().isEmpty()) {
            parameter.description(param.getDescription());
        } else {
            // Generate description for path parameters
            if (isPathParameter(paramName, path)) {
                String resource = extractResourceFromPath(path);
                parameter.description("The ID of the " + resource + ".");
            }
        }
        
        // Set schema using Swagger Core Schema
        Schema<Object> schema = new Schema<>();
        String type = mapJavaTypeToOpenApiType(param.getType());
        schema.type(type);
        
        // Add format and constraints for common parameter types
        if ("integer".equals(type)) {
            schema.format("int32");
            if (isPathParameter(paramName, path) || paramName.toLowerCase().contains("id")) {
                schema.minimum(new java.math.BigDecimal(0));
            }
        }
        
        parameter.schema(schema);
        
        return parameter;
    }
    
    /**
     * Extract the actual path parameter names from the URL path.
     * For OpenAPI compliance, parameter names must exactly match path segments.
     */
    private Set<String> extractPathParameterNames(String path) {
        Set<String> pathParams = new HashSet<>();
        String normalizedPath = normalizePath(path);
        
        // Extract all {paramName} segments from the path
        for (String segment : normalizedPath.split("/")) {
            if (segment.startsWith("{") && segment.endsWith("}")) {
                String paramName = segment.substring(1, segment.length() - 1);
                pathParams.add(paramName);
            }
        }
        
        return pathParams;
    }
    
    /**
     * Check if parameter should be treated as path parameter based on exact matching.
     * OpenAPI requires parameter names to exactly match path segments for validation.
     */
    private boolean isPathParameter(String paramName, String path) {
        Set<String> pathParamNames = extractPathParameterNames(path);
        return pathParamNames.contains(paramName);
    }
    
    private io.swagger.v3.oas.models.parameters.RequestBody buildRequestBody(ApiEndpoint.RequestBody body, 
                                                                                DtoSchemaResolver schemaResolver, 
                                                                                Map<String, Schema> dtoSchemas) {
        io.swagger.v3.oas.models.parameters.RequestBody requestBody = new io.swagger.v3.oas.models.parameters.RequestBody();
        
        if (body.getDescription() != null && !body.getDescription().trim().isEmpty()) {
            requestBody.description(body.getDescription());
        }
        requestBody.required(body.isRequired());
        
        Content content = new Content();
        for (Map.Entry<String, ApiEndpoint.MediaType> entry : body.getContent().entrySet()) {
            MediaType mediaType = new MediaType();
            Schema<Object> schema = new Schema<>();
            
            String schemaType = entry.getValue().getSchema();
            schema = buildSchemaForType(schemaType, schemaResolver, dtoSchemas);
            
            mediaType.schema(schema);
            content.addMediaType(entry.getKey(), mediaType);
        }
        requestBody.content(content);
        
        return requestBody;
    }
    
    private ApiResponse buildResponse(ApiEndpoint.Response response, 
                                      DtoSchemaResolver schemaResolver, 
                                      Map<String, Schema> dtoSchemas) {
        ApiResponse apiResponse = new ApiResponse();
        String description = response.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "Successful operation";
        }
        apiResponse.description(description);
        
        if (!response.getContent().isEmpty()) {
            Content content = new Content();
            for (Map.Entry<String, ApiEndpoint.MediaType> entry : response.getContent().entrySet()) {
                MediaType mediaType = new MediaType();
                Schema<Object> schema = new Schema<>();
                
                String schemaType = entry.getValue().getSchema();
                schema = buildSchemaForType(schemaType, schemaResolver, dtoSchemas);
                
                mediaType.schema(schema);
                content.addMediaType(entry.getKey(), mediaType);
            }
            apiResponse.content(content);
        }
        
        return apiResponse;
    }
    
    /**
     * Build schema for a given type, handling DTOs, primitives, and collections.
     */
    @SuppressWarnings("unchecked")
    private Schema<Object> buildSchemaForType(String typeName, DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        if (typeName == null || typeName.trim().isEmpty()) {
            Schema<Object> schema = new Schema<>();
            schema.type("object");
            schema.description("Unknown type");
            return schema;
        }
        
        // Handle primitive types
        if (isPrimitiveType(typeName)) {
            Schema<Object> schema = new Schema<>();
            schema.type(mapJavaTypeToOpenApiType(typeName));
            return schema;
        }
        
        // Handle collections (List<>, Set<>, etc.)
        if (typeName.startsWith("List<") || typeName.startsWith("Set<") || typeName.startsWith("Collection<")) {
            Schema<Object> schema = new Schema<>();
            schema.type("array");
            String itemType = extractGenericType(typeName);
            schema.items(buildSchemaForType(itemType, schemaResolver, dtoSchemas));
            return schema;
        }
        
        // Handle DTO classes - try to resolve schema
        String className = extractClassName(typeName);
        
        // Check if we already processed this DTO
        if (dtoSchemas.containsKey(className)) {
            // Return reference to existing schema
            Schema<Object> refSchema = new Schema<>();
            refSchema.$ref("#/components/schemas/" + className);
            return refSchema;
        }
        
        // Resolve DTO schema
        Schema<?> resolvedSchema = schemaResolver.resolveSchema(className);
        if (resolvedSchema != null) {
            dtoSchemas.put(className, resolvedSchema);
            
            // Recursively resolve any nested DTO references in the resolved schema
            resolveNestedDtoReferences(resolvedSchema, schemaResolver, dtoSchemas);
            
            // Return reference to the schema in components
            Schema<Object> refSchema = new Schema<>();
            refSchema.$ref("#/components/schemas/" + className);
            return refSchema;
        }
        
        // Fallback: create inline object schema
        Schema<Object> schema = new Schema<>();
        schema.type("object");
        schema.description("Object of type: " + typeName);
        return schema;
    }
    
    /**
     * Extract simple class name from fully qualified class name or generic type.
     * Examples: 
     * - "com.example.OwnerDto" -> "OwnerDto"
     * - "OwnerDto" -> "OwnerDto" 
     * - "ResponseEntity<OwnerDto>" -> "OwnerDto"
     */
    private String extractClassName(String typeName) {
        // Handle generic types like ResponseEntity<OwnerDto>
        if (typeName.contains("<") && typeName.contains(">")) {
            String genericPart = extractGenericType(typeName);
            if (!genericPart.equals("Object")) {
                typeName = genericPart;
            }
        }
        
        // Extract simple class name from fully qualified name
        if (typeName.contains(".")) {
            return typeName.substring(typeName.lastIndexOf('.') + 1);
        }
        
        return typeName;
    }
    
    private String mapJavaTypeToOpenApiType(String javaType) {
        switch (javaType) {
            case "String":
                return "string";
            case "int":
            case "Integer":
            case "long":
            case "Long":
                return "integer";
            case "float":
            case "Float":
            case "double":
            case "Double":
                return "number";
            case "boolean":
            case "Boolean":
                return "boolean";
            case "Date":
            case "LocalDate":
            case "LocalDateTime":
                return "string";
            default:
                return "object";
        }
    }
    
    private boolean isPrimitiveType(String type) {
        return type.equals("String") ||
               type.equals("int") || type.equals("Integer") ||
               type.equals("long") || type.equals("Long") ||
               type.equals("float") || type.equals("Float") ||
               type.equals("double") || type.equals("Double") ||
               type.equals("boolean") || type.equals("Boolean") ||
               type.equals("Date") || type.equals("LocalDate") || type.equals("LocalDateTime");
    }
    
    private String extractGenericType(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start > 0 && end > start) {
            return type.substring(start + 1, end).trim();
        }
        return "Object";
    }
    
    /**
     * Generate a human-readable summary for an operation
     */
    private String generateSummary(ApiEndpoint endpoint) {
        String method = endpoint.getHttpMethod();
        String path = endpoint.getPath();
        
        // Extract resource name from path
        String resource = extractResourceFromPath(path);
        
        switch (method.toUpperCase()) {
            case "GET":
                if (path.contains("{")) {
                    return "Get a " + resource + " by ID";
                } else {
                    return "List " + resource + "s";
                }
            case "POST":
                return "Create a " + resource;
            case "PUT":
                return "Update a " + resource + " by ID";
            case "DELETE":
                return "Delete a " + resource + " by ID";
            case "PATCH":
                return "Partially update a " + resource + " by ID";
            default:
                return "Operation on " + resource;
        }
    }
    
    /**
     * Generate a human-readable description for an operation
     */
    private String generateDescription(ApiEndpoint endpoint) {
        String method = endpoint.getHttpMethod();
        String resource = extractResourceFromPath(endpoint.getPath());
        
        switch (method.toUpperCase()) {
            case "GET":
                if (endpoint.getPath().contains("{")) {
                    return "Returns the " + resource + " or a 404 error.";
                } else {
                    return "Returns an array of " + resource + "s.";
                }
            case "POST":
                return "Creates a " + resource + ".";
            case "PUT":
                return "Updates the " + resource + " or returns a 404 error.";
            case "DELETE":
                return "Deletes the " + resource + " or returns a 404 error.";
            case "PATCH":
                return "Partially updates the " + resource + " or returns a 404 error.";
            default:
                return "Performs an operation on " + resource + ".";
        }
    }
    
    /**
     * Extract resource name from path
     */
    private String extractResourceFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "resource";
        }
        
        // Remove parameters and extract the last meaningful segment
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (!segment.isEmpty() && !segment.contains("{") && !segment.equals("api")) {
                // Convert from plural to singular and clean up
                String resource = segment.toLowerCase();
                if (resource.endsWith("ies")) {
                    resource = resource.substring(0, resource.length() - 3) + "y";
                } else if (resource.endsWith("s") && !resource.endsWith("ss")) {
                    resource = resource.substring(0, resource.length() - 1);
                }
                return resource;
            }
        }
        return "resource";
    }
    
    /**
     * Add default response codes based on HTTP method
     */
    private void addDefaultResponses(ApiResponses responses, ApiEndpoint endpoint, 
                                   DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        String method = endpoint.getHttpMethod().toUpperCase();
        String resource = extractResourceFromPath(endpoint.getPath());
        
        // Success response with appropriate content
        ApiResponse successResponse = new ApiResponse();
        
        // Add response content if there are response definitions
        if (!endpoint.getResponses().isEmpty()) {
            // Use the first response to determine content type
            ApiEndpoint.Response firstResponse = endpoint.getResponses().values().iterator().next();
            if (firstResponse.getContent() != null && !firstResponse.getContent().isEmpty()) {
                Content content = new Content();
                for (Map.Entry<String, ApiEndpoint.MediaType> entry : firstResponse.getContent().entrySet()) {
                    MediaType mediaType = new MediaType();
                    mediaType.schema(buildSchemaForType(entry.getValue().getSchema(), schemaResolver, dtoSchemas));
                    content.addMediaType(entry.getKey(), mediaType);
                }
                successResponse.content(content);
            }
        }
        
        switch (method) {
            case "POST":
                successResponse.description(resource + " created successfully.");
                responses.addApiResponse("201", successResponse);
                break;
            case "DELETE":
                successResponse.description(resource + " deleted successfully.");
                responses.addApiResponse("200", successResponse);
                break;
            case "GET":
                if (endpoint.getPath().contains("{")) {
                    successResponse.description(resource + " details found and returned.");
                } else {
                    successResponse.description("List of " + resource + "s returned successfully.");
                }
                responses.addApiResponse("200", successResponse);
                
                // Add 304 Not Modified for GET operations (caching)
                addResponseWithContent(responses, "304", "Not modified.", successResponse.getContent());
                break;
            case "PUT":
            case "PATCH":
                successResponse.description(resource + " updated successfully.");
                responses.addApiResponse("200", successResponse);
                
                // Add 304 Not Modified for update operations
                addResponseWithContent(responses, "304", "Not modified.", successResponse.getContent());
                break;
            default:
                successResponse.description("Successful response");
                responses.addApiResponse("200", successResponse);
        }
        
        // Standard error responses with JSON content
        addErrorResponseWithJsonContent(responses, "400", "Bad request.", schemaResolver, dtoSchemas);
        
        if (endpoint.getPath().contains("{")) {
            addErrorResponseWithJsonContent(responses, "404", resource + " not found.", schemaResolver, dtoSchemas);
        }
        
        addErrorResponseWithJsonContent(responses, "500", "Server error.", schemaResolver, dtoSchemas);
    }
    
    /**
     * Add a standard error response
     */
    private void addErrorResponse(ApiResponses responses, String code, String description) {
        ApiResponse errorResponse = new ApiResponse();
        errorResponse.description(description);
        responses.addApiResponse(code, errorResponse);
    }
    
    /**
     * Add response with existing content
     */
    private void addResponseWithContent(ApiResponses responses, String code, String description, Content existingContent) {
        ApiResponse response = new ApiResponse();
        response.description(description);
        if (existingContent != null) {
            response.content(existingContent);
        }
        responses.addApiResponse(code, response);
    }
    
    /**
     * Add error response with JSON content containing ProblemDetail schema
     */
    private void addErrorResponseWithJsonContent(ApiResponses responses, String code, String description, 
                                                DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        ApiResponse errorResponse = new ApiResponse();
        errorResponse.description(description);
        
        // Add JSON content with ProblemDetail schema reference
        Content content = new Content();
        MediaType mediaType = new MediaType();
        
        // Try to resolve ProblemDetail schema, fallback to generic error schema
        Schema<Object> errorSchema = buildSchemaForType("ProblemDetail", schemaResolver, dtoSchemas);
        if (errorSchema.get$ref() == null) {
            // If ProblemDetail not found, create a generic error schema
            errorSchema = new Schema<>();
            errorSchema.type("object");
            errorSchema.description("Error response");
            
            // Add common error properties
            Map<String, Schema> properties = new HashMap<>();
            Schema<Object> titleSchema = new Schema<>();
            titleSchema.type("string");
            titleSchema.description("Error title");
            properties.put("title", titleSchema);
            
            Schema<Object> detailSchema = new Schema<>();
            detailSchema.type("string");
            detailSchema.description("Error detail");
            properties.put("detail", detailSchema);
            
            Schema<Object> statusSchema = new Schema<>();
            statusSchema.type("integer");
            statusSchema.description("HTTP status code");
            properties.put("status", statusSchema);
            
            errorSchema.properties(properties);
        }
        
        mediaType.schema(errorSchema);
        content.addMediaType("application/json", mediaType);
        errorResponse.content(content);
        
        responses.addApiResponse(code, errorResponse);
    }
    
    /**
     * Add missing standard responses (304, 400, 404, 500) when explicit responses are provided
     */
    private void addMissingStandardResponses(ApiResponses responses, ApiEndpoint endpoint, 
                                           DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        String method = endpoint.getHttpMethod().toUpperCase();
        String resource = extractResourceFromPath(endpoint.getPath());
        
        // Add 304 Not Modified for GET and PUT operations if not already present
        if (("GET".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) && 
            !responses.containsKey("304")) {
            
            // For 304, try to reuse existing success response content
            Content existingContent = null;
            if (responses.containsKey("200")) {
                existingContent = responses.get("200").getContent();
            }
            addResponseWithContent(responses, "304", "Not modified.", existingContent);
        }
        
        // Add standard error responses if not already present
        if (!responses.containsKey("400")) {
            addErrorResponseWithJsonContent(responses, "400", "Bad request.", schemaResolver, dtoSchemas);
        }
        
        if (endpoint.getPath().contains("{") && !responses.containsKey("404")) {
            addErrorResponseWithJsonContent(responses, "404", resource + " not found.", schemaResolver, dtoSchemas);
        }
        
        if (!responses.containsKey("500")) {
            addErrorResponseWithJsonContent(responses, "500", "Server error.", schemaResolver, dtoSchemas);
        }
    }
    
    /**
     * Recursively resolve nested DTO references within a schema.
     * This ensures that all referenced DTOs are included in the components section.
     */
    private void resolveNestedDtoReferences(Schema<?> schema, DtoSchemaResolver schemaResolver, Map<String, Schema> dtoSchemas) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        
        for (Map.Entry<String, Schema> property : schema.getProperties().entrySet()) {
            Schema propertySchema = property.getValue();
            
            // Handle direct DTO references
            if (propertySchema.get$ref() != null) {
                String ref = propertySchema.get$ref();
                if (ref.startsWith("#/components/schemas/")) {
                    String referencedClassName = ref.substring("#/components/schemas/".length());
                    
                    // Ensure the referenced DTO is resolved
                    if (!dtoSchemas.containsKey(referencedClassName)) {
                        Schema<?> referencedSchema = schemaResolver.resolveSchema(referencedClassName);
                        if (referencedSchema != null) {
                            dtoSchemas.put(referencedClassName, referencedSchema);
                            // Recursively resolve nested references in the referenced schema
                            resolveNestedDtoReferences(referencedSchema, schemaResolver, dtoSchemas);
                        }
                    }
                }
            }
            
            // Handle array items that might be DTO references
            if ("array".equals(propertySchema.getType()) && propertySchema.getItems() != null) {
                Schema itemsSchema = propertySchema.getItems();
                if (itemsSchema.get$ref() != null) {
                    String ref = itemsSchema.get$ref();
                    if (ref.startsWith("#/components/schemas/")) {
                        String referencedClassName = ref.substring("#/components/schemas/".length());
                        
                        // Ensure the referenced DTO is resolved
                        if (!dtoSchemas.containsKey(referencedClassName)) {
                            Schema<?> referencedSchema = schemaResolver.resolveSchema(referencedClassName);
                            if (referencedSchema != null) {
                                dtoSchemas.put(referencedClassName, referencedSchema);
                                // Recursively resolve nested references in the referenced schema
                                resolveNestedDtoReferences(referencedSchema, schemaResolver, dtoSchemas);
                            }
                        }
                    }
                }
            }
            
            // Recursively process nested object properties
            if ("object".equals(propertySchema.getType())) {
                resolveNestedDtoReferences(propertySchema, schemaResolver, dtoSchemas);
            }
        }
    }
}