package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class OpenApiGenerator {
    
    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    
    public OpenApiGenerator() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }
    
    public String generate(ScanResult scanResult, ApiScanCLI.OutputFormat format) {
        OpenAPI openApi = buildOpenApiSpec(scanResult);
        
        try {
            if (format == ApiScanCLI.OutputFormat.json) {
                return jsonMapper.writeValueAsString(openApi);
            } else {
                return yamlMapper.writeValueAsString(openApi);
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
        Map<String, Set<String>> taggedOperations = new HashMap<>();
        
        for (ApiEndpoint endpoint : scanResult.getEndpoints()) {
            addEndpointToSpec(endpoint, paths, taggedOperations);
        }
        
        openApi.paths(paths);
        
        // Add tags
        List<io.swagger.v3.oas.models.tags.Tag> tags = taggedOperations.keySet().stream()
            .sorted()
            .map(tagName -> {
                io.swagger.v3.oas.models.tags.Tag tag = new io.swagger.v3.oas.models.tags.Tag();
                tag.setName(tagName);
                return tag;
            })
            .collect(Collectors.toList());
        
        if (!tags.isEmpty()) {
            openApi.tags(tags);
        }
        
        // Add components (schemas, etc.)
        Components components = new Components();
        openApi.components(components);
        
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
    
    private void addEndpointToSpec(ApiEndpoint endpoint, Paths paths, Map<String, Set<String>> taggedOperations) {
        String path = endpoint.getPath();
        PathItem pathItem = paths.get(path);
        
        if (pathItem == null) {
            pathItem = new PathItem();
            paths.addPathItem(path, pathItem);
        }
        
        Operation operation = buildOperation(endpoint, taggedOperations);
        
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
    
    private Operation buildOperation(ApiEndpoint endpoint, Map<String, Set<String>> taggedOperations) {
        Operation operation = new Operation();
        
        // Set operation ID
        operation.operationId(endpoint.getOperationId());
        
        // Set summary and description
        if (endpoint.getSummary() != null) {
            operation.summary(endpoint.getSummary());
        }
        if (endpoint.getDescription() != null) {
            operation.description(endpoint.getDescription());
        }
        
        // Set tags
        List<String> tags = endpoint.getTags();
        if (tags.isEmpty()) {
            // Use controller class name as default tag
            String tag = endpoint.getControllerClass();
            if (tag != null) {
                tag = tag.replace("Controller", "")
                         .replace("RestController", "");
                tags = Collections.singletonList(tag);
            }
        }
        
        if (!tags.isEmpty()) {
            operation.tags(tags);
            for (String tag : tags) {
                taggedOperations.computeIfAbsent(tag, k -> new HashSet<>())
                    .add(endpoint.getOperationId());
            }
        }
        
        // Set parameters
        if (!endpoint.getParameters().isEmpty()) {
            List<Parameter> parameters = new ArrayList<>();
            for (ApiEndpoint.Parameter param : endpoint.getParameters()) {
                parameters.add(buildParameter(param));
            }
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
    
    private Parameter buildParameter(ApiEndpoint.Parameter param) {
        Parameter parameter;
        
        switch (param.getIn()) {
            case "path":
                parameter = new PathParameter();
                break;
            case "header":
                parameter = new HeaderParameter();
                break;
            case "query":
            default:
                parameter = new QueryParameter();
                break;
        }
        
        parameter.name(param.getName());
        parameter.description(param.getDescription());
        parameter.required(param.isRequired());
        
        // Set schema
        Schema<?> schema = new Schema<>();
        schema.type(mapJavaTypeToOpenApiType(param.getType()));
        parameter.schema(schema);
        
        return parameter;
    }
    
    private RequestBody buildRequestBody(ApiEndpoint.RequestBody body) {
        RequestBody requestBody = new RequestBody();
        requestBody.description(body.getDescription());
        requestBody.required(body.isRequired());
        
        Content content = new Content();
        for (Map.Entry<String, ApiEndpoint.MediaType> entry : body.getContent().entrySet()) {
            MediaType mediaType = new MediaType();
            Schema<?> schema = new Schema<>();
            schema.$ref("#/components/schemas/" + sanitizeSchemaName(entry.getValue().getSchema()));
            mediaType.schema(schema);
            content.addMediaType(entry.getKey(), mediaType);
        }
        requestBody.content(content);
        
        return requestBody;
    }
    
    private ApiResponse buildResponse(ApiEndpoint.Response response) {
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.description(response.getDescription());
        
        if (!response.getContent().isEmpty()) {
            Content content = new Content();
            for (Map.Entry<String, ApiEndpoint.MediaType> entry : response.getContent().entrySet()) {
                MediaType mediaType = new MediaType();
                Schema<?> schema = new Schema<>();
                
                String schemaType = entry.getValue().getSchema();
                if (isPrimitiveType(schemaType)) {
                    schema.type(mapJavaTypeToOpenApiType(schemaType));
                } else if (schemaType.startsWith("List<") || schemaType.startsWith("Set<")) {
                    schema.type("array");
                    Schema<?> itemSchema = new Schema<>();
                    String itemType = extractGenericType(schemaType);
                    if (isPrimitiveType(itemType)) {
                        itemSchema.type(mapJavaTypeToOpenApiType(itemType));
                    } else {
                        itemSchema.$ref("#/components/schemas/" + sanitizeSchemaName(itemType));
                    }
                    schema.items(itemSchema);
                } else {
                    schema.$ref("#/components/schemas/" + sanitizeSchemaName(schemaType));
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
               type.equals("boolean") || type.equals("Boolean");
    }
    
    private String extractGenericType(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start > 0 && end > start) {
            return type.substring(start + 1, end).trim();
        }
        return "Object";
    }
    
    private String sanitizeSchemaName(String name) {
        // Remove package names and keep only simple class name
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        // Remove generic type parameters
        if (name.contains("<")) {
            name = name.substring(0, name.indexOf('<'));
        }
        return name;
    }
}