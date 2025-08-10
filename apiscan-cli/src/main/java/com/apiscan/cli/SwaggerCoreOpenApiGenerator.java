package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
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
        
        // Build paths
        Paths paths = new Paths();
        Set<String> usedTags = new HashSet<>();
        
        for (ApiEndpoint endpoint : scanResult.getEndpoints()) {
            addEndpointToSpec(endpoint, paths, usedTags);
        }
        
        openApi.paths(paths);
        
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
    
    private void addEndpointToSpec(ApiEndpoint endpoint, Paths paths, Set<String> usedTags) {
        String path = normalizePath(endpoint.getPath());
        PathItem pathItem = paths.get(path);
        
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.addPathItem(path, pathItem);
        }
        
        Operation operation = buildOperation(endpoint, usedTags);
        
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
    
    private Operation buildOperation(ApiEndpoint endpoint, Set<String> usedTags) {
        Operation operation = new Operation();
        
        // Set operation ID
        operation.operationId(endpoint.getOperationId());
        
        // Set summary and description
        if (endpoint.getSummary() != null && !endpoint.getSummary().trim().isEmpty()) {
            operation.summary(endpoint.getSummary());
        }
        if (endpoint.getDescription() != null && !endpoint.getDescription().trim().isEmpty()) {
            operation.description(endpoint.getDescription());
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
            operation.requestBody(buildRequestBody(endpoint.getRequestBody()));
        }
        
        // Set responses
        ApiResponses responses = new ApiResponses();
        if (endpoint.getResponses().isEmpty()) {
            // Add default response
            ApiResponse defaultResponse = new ApiResponse();
            defaultResponse.description("Successful operation");
            responses.addApiResponse("200", defaultResponse);
        } else {
            for (Map.Entry<String, ApiEndpoint.Response> entry : endpoint.getResponses().entrySet()) {
                responses.addApiResponse(entry.getKey(), buildResponse(entry.getValue()));
            }
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
        }
        
        // Set schema using Swagger Core Schema
        Schema<Object> schema = new Schema<>();
        schema.type(mapJavaTypeToOpenApiType(param.getType()));
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
    
    private io.swagger.v3.oas.models.parameters.RequestBody buildRequestBody(ApiEndpoint.RequestBody body) {
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
            if (isPrimitiveType(schemaType)) {
                schema.type(mapJavaTypeToOpenApiType(schemaType));
            } else {
                // Use generic object type with description
                schema.type("object");
                schema.description("Request body of type: " + schemaType);
            }
            
            mediaType.schema(schema);
            content.addMediaType(entry.getKey(), mediaType);
        }
        requestBody.content(content);
        
        return requestBody;
    }
    
    private ApiResponse buildResponse(ApiEndpoint.Response response) {
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
                if (isPrimitiveType(schemaType)) {
                    schema.type(mapJavaTypeToOpenApiType(schemaType));
                } else if (schemaType.startsWith("List<") || schemaType.startsWith("Set<")) {
                    schema.type("array");
                    Schema<Object> itemSchema = new Schema<>();
                    String itemType = extractGenericType(schemaType);
                    if (isPrimitiveType(itemType)) {
                        itemSchema.type(mapJavaTypeToOpenApiType(itemType));
                    } else {
                        itemSchema.type("object");
                        itemSchema.description("Array item of type: " + itemType);
                    }
                    schema.items(itemSchema);
                } else {
                    schema.type("object");
                    schema.description("Response of type: " + schemaType);
                }
                
                mediaType.schema(schema);
                content.addMediaType(entry.getKey(), mediaType);
            }
            apiResponse.content(content);
        }
        
        return apiResponse;
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
}