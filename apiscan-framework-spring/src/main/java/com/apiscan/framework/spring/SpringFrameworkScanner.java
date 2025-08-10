package com.apiscan.framework.spring;

import com.apiscan.core.framework.FrameworkScanner;
import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import com.apiscan.core.parser.JavaSourceParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpringFrameworkScanner implements FrameworkScanner {
    private static final Logger logger = LoggerFactory.getLogger(SpringFrameworkScanner.class);
    
    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
        "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
        "RequestMapping"
    );
    
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
        "RestController", "Controller"
    );
    
    @Override
    public ScanResult scan(Path projectPath) {
        logger.info("Starting Spring framework scan for project: {}", projectPath);
        ScanResult result = new ScanResult();
        result.setProjectPath(projectPath.toString());
        result.setFramework(getFrameworkName());
        
        long startTime = System.currentTimeMillis();
        JavaSourceParser parser = new JavaSourceParser(projectPath);
        
        // Find all Java files
        List<Path> javaFiles = findJavaFiles(projectPath);
        result.setFilesScanned(javaFiles.size());
        
        for (Path javaFile : javaFiles) {
            try {
                scanFile(javaFile, parser, result);
            } catch (Exception e) {
                logger.error("Error scanning file {}: {}", javaFile, e.getMessage());
                result.addError("Error scanning " + javaFile + ": " + e.getMessage());
            }
        }
        
        result.setScanDurationMs(System.currentTimeMillis() - startTime);
        logger.info("Spring framework scan completed. Found {} endpoints in {} ms", 
            result.getEndpoints().size(), result.getScanDurationMs());
        
        return result;
    }
    
    private void scanFile(Path javaFile, JavaSourceParser parser, ScanResult result) {
        Optional<CompilationUnit> cuOpt = parser.parseFile(javaFile);
        if (!cuOpt.isPresent()) {
            return;
        }
        
        CompilationUnit cu = cuOpt.get();
        List<ClassOrInterfaceDeclaration> classes = parser.findClasses(cu);
        
        for (ClassOrInterfaceDeclaration clazz : classes) {
            if (!isController(clazz)) {
                continue;
            }
            
            String baseUrl = extractBaseUrl(clazz);
            String className = clazz.getNameAsString();
            
            for (MethodDeclaration method : clazz.getMethods()) {
                Optional<ApiEndpoint> endpoint = extractEndpoint(method, baseUrl, className);
                endpoint.ifPresent(result::addEndpoint);
            }
        }
    }
    
    private boolean isController(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotations().stream()
            .anyMatch(ann -> CONTROLLER_ANNOTATIONS.contains(ann.getNameAsString()));
    }
    
    private String extractBaseUrl(ClassOrInterfaceDeclaration clazz) {
        Optional<AnnotationExpr> requestMapping = clazz.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("RequestMapping"))
            .findFirst();
        
        if (requestMapping.isPresent()) {
            return extractPath(requestMapping.get());
        }
        
        return "";
    }
    
    private Optional<ApiEndpoint> extractEndpoint(MethodDeclaration method, String baseUrl, String className) {
        Optional<AnnotationExpr> mappingAnnotation = method.getAnnotations().stream()
            .filter(ann -> HTTP_METHOD_ANNOTATIONS.contains(ann.getNameAsString()))
            .findFirst();
        
        if (!mappingAnnotation.isPresent()) {
            return Optional.empty();
        }
        
        AnnotationExpr annotation = mappingAnnotation.get();
        ApiEndpoint endpoint = new ApiEndpoint();
        
        // Set basic information
        endpoint.setControllerClass(className);
        endpoint.setMethodName(method.getNameAsString());
        endpoint.setOperationId(className + "_" + method.getNameAsString());
        
        // Extract HTTP method
        String httpMethod = extractHttpMethod(annotation);
        endpoint.setHttpMethod(httpMethod);
        
        // Extract path
        String path = extractPath(annotation);
        endpoint.setPath(combinePaths(baseUrl, path));
        
        // Extract parameters
        extractParameters(method, endpoint);
        
        // Extract request/response types
        extractRequestBody(method, endpoint);
        extractResponseType(method, endpoint);
        
        // Check if deprecated
        boolean deprecated = method.getAnnotations().stream()
            .anyMatch(ann -> ann.getNameAsString().equals("Deprecated"));
        endpoint.setDeprecated(deprecated);
        
        // Extract JavaDoc if available
        method.getJavadoc().ifPresent(javadoc -> {
            endpoint.setDescription(javadoc.getDescription().toText());
        });
        
        return Optional.of(endpoint);
    }
    
    private String extractHttpMethod(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        
        switch (name) {
            case "GetMapping":
                return "GET";
            case "PostMapping":
                return "POST";
            case "PutMapping":
                return "PUT";
            case "DeleteMapping":
                return "DELETE";
            case "PatchMapping":
                return "PATCH";
            case "RequestMapping":
                return extractMethodFromRequestMapping(annotation);
            default:
                return "GET";
        }
    }
    
    private String extractMethodFromRequestMapping(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnn = (NormalAnnotationExpr) annotation;
            Optional<MemberValuePair> methodPair = normalAnn.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("method"))
                .findFirst();
            
            if (methodPair.isPresent()) {
                String value = methodPair.get().getValue().toString();
                if (value.contains("GET")) return "GET";
                if (value.contains("POST")) return "POST";
                if (value.contains("PUT")) return "PUT";
                if (value.contains("DELETE")) return "DELETE";
                if (value.contains("PATCH")) return "PATCH";
            }
        }
        return "GET";
    }
    
    private String extractPath(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleAnn = (SingleMemberAnnotationExpr) annotation;
            return cleanPath(singleAnn.getMemberValue().toString());
        } else if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnn = (NormalAnnotationExpr) annotation;
            
            // Look for "value" or "path" attribute
            Optional<MemberValuePair> pathPair = normalAnn.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("value") || 
                              pair.getNameAsString().equals("path"))
                .findFirst();
            
            if (pathPair.isPresent()) {
                Expression value = pathPair.get().getValue();
                if (value instanceof ArrayInitializerExpr) {
                    ArrayInitializerExpr array = (ArrayInitializerExpr) value;
                    if (!array.getValues().isEmpty()) {
                        return cleanPath(array.getValues().get(0).toString());
                    }
                } else {
                    return cleanPath(value.toString());
                }
            }
        }
        return "";
    }
    
    private String cleanPath(String path) {
        // Remove quotes and clean up the path
        return path.replaceAll("^\"|\"$", "")
                  .replaceAll("^'|'$", "");
    }
    
    private String combinePaths(String basePath, String methodPath) {
        if (basePath.isEmpty()) {
            return methodPath.isEmpty() ? "/" : methodPath;
        }
        if (methodPath.isEmpty()) {
            return basePath;
        }
        
        String combined = basePath;
        if (!combined.endsWith("/")) {
            combined += "/";
        }
        if (methodPath.startsWith("/")) {
            methodPath = methodPath.substring(1);
        }
        return combined + methodPath;
    }
    
    private void extractParameters(MethodDeclaration method, ApiEndpoint endpoint) {
        for (Parameter param : method.getParameters()) {
            Optional<ApiEndpoint.Parameter> apiParam = extractParameter(param);
            apiParam.ifPresent(p -> endpoint.getParameters().add(p));
        }
    }
    
    private Optional<ApiEndpoint.Parameter> extractParameter(Parameter param) {
        ApiEndpoint.Parameter apiParam = new ApiEndpoint.Parameter();
        apiParam.setName(param.getNameAsString());
        apiParam.setType(param.getTypeAsString());
        
        // Check for Spring annotations
        Optional<AnnotationExpr> pathVariable = param.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("PathVariable"))
            .findFirst();
        
        if (pathVariable.isPresent()) {
            apiParam.setIn("path");
            apiParam.setRequired(true);
            extractParameterName(pathVariable.get(), apiParam);
            return Optional.of(apiParam);
        }
        
        Optional<AnnotationExpr> requestParam = param.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("RequestParam"))
            .findFirst();
        
        if (requestParam.isPresent()) {
            apiParam.setIn("query");
            extractRequestParamDetails(requestParam.get(), apiParam);
            return Optional.of(apiParam);
        }
        
        Optional<AnnotationExpr> requestHeader = param.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("RequestHeader"))
            .findFirst();
        
        if (requestHeader.isPresent()) {
            apiParam.setIn("header");
            extractParameterName(requestHeader.get(), apiParam);
            return Optional.of(apiParam);
        }
        
        // Check if it's a request body
        boolean isRequestBody = param.getAnnotations().stream()
            .anyMatch(ann -> ann.getNameAsString().equals("RequestBody"));
        
        if (isRequestBody) {
            // This will be handled in extractRequestBody
            return Optional.empty();
        }
        
        // If no annotation, it might be a simple type that Spring treats as request param
        String type = param.getTypeAsString();
        if (isSimpleType(type)) {
            apiParam.setIn("query");
            apiParam.setRequired(false);
            return Optional.of(apiParam);
        }
        
        return Optional.empty();
    }
    
    private void extractParameterName(AnnotationExpr annotation, ApiEndpoint.Parameter param) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            SingleMemberAnnotationExpr singleAnn = (SingleMemberAnnotationExpr) annotation;
            String value = cleanPath(singleAnn.getMemberValue().toString());
            if (!value.isEmpty()) {
                param.setName(value);
            }
        } else if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnn = (NormalAnnotationExpr) annotation;
            normalAnn.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("value") || 
                              pair.getNameAsString().equals("name"))
                .findFirst()
                .ifPresent(pair -> {
                    String value = cleanPath(pair.getValue().toString());
                    if (!value.isEmpty()) {
                        param.setName(value);
                    }
                });
        }
    }
    
    private void extractRequestParamDetails(AnnotationExpr annotation, ApiEndpoint.Parameter param) {
        extractParameterName(annotation, param);
        
        // Default required is true for @RequestParam
        param.setRequired(true);
        
        if (annotation instanceof NormalAnnotationExpr) {
            NormalAnnotationExpr normalAnn = (NormalAnnotationExpr) annotation;
            normalAnn.getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("required"))
                .findFirst()
                .ifPresent(pair -> {
                    String value = pair.getValue().toString();
                    param.setRequired(!value.equals("false"));
                });
        }
    }
    
    private boolean isSimpleType(String type) {
        return type.equals("String") ||
               type.equals("int") || type.equals("Integer") ||
               type.equals("long") || type.equals("Long") ||
               type.equals("boolean") || type.equals("Boolean") ||
               type.equals("double") || type.equals("Double") ||
               type.equals("float") || type.equals("Float");
    }
    
    private void extractRequestBody(MethodDeclaration method, ApiEndpoint endpoint) {
        for (Parameter param : method.getParameters()) {
            boolean isRequestBody = param.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("RequestBody"));
            
            if (isRequestBody) {
                ApiEndpoint.RequestBody body = new ApiEndpoint.RequestBody();
                body.setRequired(true);
                
                ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
                mediaType.setSchema(param.getTypeAsString());
                
                body.getContent().put("application/json", mediaType);
                endpoint.setRequestBody(body);
                
                // Default consumes
                endpoint.getConsumes().add("application/json");
                break;
            }
        }
    }
    
    private void extractResponseType(MethodDeclaration method, ApiEndpoint endpoint) {
        String returnType = method.getTypeAsString();
        
        ApiEndpoint.Response response = new ApiEndpoint.Response();
        response.setDescription("Successful response");
        
        if (!returnType.equals("void")) {
            ApiEndpoint.MediaType mediaType = new ApiEndpoint.MediaType();
            
            // Handle ResponseEntity
            if (returnType.startsWith("ResponseEntity")) {
                // Extract generic type
                int start = returnType.indexOf('<');
                int end = returnType.lastIndexOf('>');
                if (start > 0 && end > start) {
                    String genericType = returnType.substring(start + 1, end);
                    mediaType.setSchema(genericType);
                } else {
                    mediaType.setSchema("Object");
                }
            } else {
                mediaType.setSchema(returnType);
            }
            
            response.getContent().put("application/json", mediaType);
            
            // Default produces
            endpoint.getProduces().add("application/json");
        }
        
        endpoint.getResponses().put("200", response);
    }
    
    private List<Path> findJavaFiles(Path projectPath) {
        List<Path> javaFiles = new ArrayList<>();
        Path srcPath = projectPath.resolve("src/main/java");
        
        if (!Files.exists(srcPath)) {
            logger.warn("Source directory not found: {}", srcPath);
            return javaFiles;
        }
        
        try (Stream<Path> paths = Files.walk(srcPath)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error finding Java files: {}", e.getMessage());
        }
        
        return javaFiles;
    }
    
    @Override
    public String getFrameworkName() {
        return "Spring";
    }
}