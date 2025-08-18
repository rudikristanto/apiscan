package com.apiscan.cli;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Resolves DTO class schemas by parsing generated or manual DTO classes.
 * Handles both generated OpenAPI DTOs and manual DTOs.
 */
public class DtoSchemaResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(DtoSchemaResolver.class);
    
    private final Map<String, Schema<?>> schemaCache = new HashMap<>();
    private final String projectPath;
    
    public DtoSchemaResolver(String projectPath) {
        this.projectPath = projectPath;
    }
    
    /**
     * Resolve schema for a DTO class name.
     * First checks if the DTO source exists and parses it.
     * If not found, returns a placeholder schema.
     */
    public Schema<?> resolveSchema(String className) {
        if (className == null || className.trim().isEmpty()) {
            return createPlaceholderSchema("Unknown");
        }
        
        // Check cache first
        if (schemaCache.containsKey(className)) {
            return schemaCache.get(className);
        }
        
        Schema<?> schema = null;
        
        // Try to find and parse the DTO class
        Optional<Path> dtoFile = findDtoFile(className);
        if (dtoFile.isPresent()) {
            schema = parseSchemaFromFile(dtoFile.get(), className);
        }
        
        // If parsing failed or file not found, create placeholder
        if (schema == null) {
            schema = createPlaceholderSchema(className);
            logger.debug("Could not find or parse DTO class '{}', using placeholder schema", className);
        } else {
            logger.debug("Successfully resolved schema for DTO class '{}'", className);
        }
        
        // Cache the result
        schemaCache.put(className, schema);
        return schema;
    }
    
    /**
     * Find DTO file in common locations within the project.
     */
    private Optional<Path> findDtoFile(String className) {
        // Common locations for DTO files
        List<String> searchPaths = Arrays.asList(
            // Generated DTOs (OpenAPI)
            "target/generated-sources/openapi/src/main/java",
            "build/generated-sources/openapi/src/main/java", 
            
            // Manual DTOs
            "src/main/java",
            
            // Other possible locations
            "target/generated-sources/annotations",
            "build/generated/sources/annotationProcessor/java/main"
        );
        
        for (String searchPath : searchPaths) {
            try {
                Path basePath = Paths.get(projectPath, searchPath);
                if (Files.exists(basePath)) {
                    Optional<Path> found = findFileRecursively(basePath, className + ".java");
                    if (found.isPresent()) {
                        logger.debug("Found DTO file for '{}' at: {}", className, found.get());
                        return found;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error searching for DTO '{}' in path '{}': {}", className, searchPath, e.getMessage());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Recursively search for a file by name in directory tree.
     */
    private Optional<Path> findFileRecursively(Path dir, String fileName) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst();
        } catch (IOException e) {
            logger.debug("Error walking directory '{}': {}", dir, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Parse schema from DTO file using JavaParser.
     */
    private Schema<?> parseSchemaFromFile(Path file, String className) {
        try {
            String content = Files.readString(file);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElse(null);
            
            if (cu == null) {
                return null;
            }
            
            // Find the class declaration
            Optional<ClassOrInterfaceDeclaration> classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class,
                cd -> cd.getNameAsString().equals(className));
            
            if (classDecl.isEmpty()) {
                return null;
            }
            
            return parseClassSchema(classDecl.get(), file);
            
        } catch (Exception e) {
            logger.debug("Error parsing DTO file '{}': {}", file, e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse schema from class declaration.
     */
    private Schema<?> parseClassSchema(ClassOrInterfaceDeclaration classDecl, Path sourceFile) {
        Schema<Object> schema = new Schema<>();
        schema.type("object");
        
        // Set description
        String description = extractClassDescription(classDecl, sourceFile);
        schema.description(description);
        
        // Parse fields
        Map<String, Schema> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        for (FieldDeclaration field : classDecl.getFields()) {
            if (field.isPublic() || hasGetter(classDecl, field)) {
                parseFieldSchema(field, properties, required);
            }
        }
        
        if (!properties.isEmpty()) {
            schema.properties(properties);
        }
        
        if (!required.isEmpty()) {
            schema.required(required);
        }
        
        return schema;
    }
    
    /**
     * Parse schema for individual field.
     */
    private void parseFieldSchema(FieldDeclaration field, Map<String, Schema> properties, List<String> required) {
        for (VariableDeclarator var : field.getVariables()) {
            String fieldName = var.getNameAsString();
            String fieldType = var.getTypeAsString();
            
            Schema<Object> fieldSchema = buildFieldSchema(fieldType);
            
            // Check for validation annotations to determine if required
            boolean isRequired = hasRequiredAnnotation(field);
            if (isRequired) {
                required.add(fieldName);
            }
            
            // Add field description if available (only if not already set by buildFieldSchema)
            if (fieldSchema.getDescription() == null) {
                String fieldDescription = extractFieldDescription(field, fieldName, fieldType);
                if (fieldDescription != null) {
                    fieldSchema.description(fieldDescription);
                }
            }
            
            properties.put(fieldName, fieldSchema);
        }
    }
    
    /**
     * Build schema for a field, handling DTO references properly.
     */
    private Schema<Object> buildFieldSchema(String fieldType) {
        Schema<Object> fieldSchema = new Schema<>();
        
        if (isPrimitiveType(fieldType)) {
            setSchemaType(fieldSchema, fieldType);
        } else if (isCollectionType(fieldType)) {
            fieldSchema.type("array");
            String itemType = extractGenericType(fieldType);
            Schema<Object> itemSchema = buildFieldSchema(itemType);
            fieldSchema.items(itemSchema);
        } else {
            // Check if this is a DTO class that should be referenced
            String simpleClassName = extractSimpleClassName(fieldType);
            if (isDtoClass(simpleClassName)) {
                // Create a reference to the DTO schema
                fieldSchema.$ref("#/components/schemas/" + simpleClassName);
            } else {
                // Fall back to generic object description
                fieldSchema.type("object");
                fieldSchema.description("Object of type: " + fieldType);
            }
        }
        
        return fieldSchema;
    }
    
    /**
     * Check if the given type is a primitive type.
     */
    private boolean isPrimitiveType(String type) {
        return type.equals("String") || 
               type.equals("Integer") || type.equals("int") ||
               type.equals("Long") || type.equals("long") ||
               type.equals("Double") || type.equals("double") ||
               type.equals("Float") || type.equals("float") ||
               type.equals("Boolean") || type.equals("boolean") ||
               type.equals("BigDecimal") ||
               type.equals("Date") || type.equals("LocalDate") || 
               type.equals("LocalDateTime") || type.equals("Instant");
    }
    
    /**
     * Check if the given type is a collection type.
     */
    private boolean isCollectionType(String type) {
        return type.startsWith("List<") || type.startsWith("Set<") || 
               type.startsWith("Collection<") || type.equals("List") || 
               type.equals("Set") || type.equals("Collection");
    }
    
    /**
     * Extract simple class name from type (com.example.PetTypeDto -> PetTypeDto).
     */
    private String extractSimpleClassName(String type) {
        // Remove generic parameters
        if (type.contains("<")) {
            type = type.substring(0, type.indexOf("<"));
        }
        
        // Extract simple name
        if (type.contains(".")) {
            return type.substring(type.lastIndexOf('.') + 1);
        }
        
        return type;
    }
    
    /**
     * Check if a class name represents a DTO that should be referenced.
     */
    private boolean isDtoClass(String className) {
        // Common DTO naming patterns
        return className.endsWith("Dto") || 
               className.endsWith("DTO") || 
               className.endsWith("Response") || 
               className.endsWith("Request") || 
               className.endsWith("Entity") ||
               className.endsWith("Model") ||
               // Check if we've already resolved this schema
               schemaCache.containsKey(className);
    }
    
    /**
     * Set OpenAPI schema type based on Java type.
     */
    private void setSchemaType(Schema<Object> schema, String javaType) {
        // Handle generics (List<String>, etc.)
        String baseType = javaType.replaceAll("<.*>", "").trim();
        
        switch (baseType) {
            case "String":
                schema.type("string");
                break;
            case "Integer":
            case "int":
            case "Long":
            case "long":
                schema.type("integer");
                break;
            case "Double":
            case "double":
            case "Float":
            case "float":
            case "BigDecimal":
                schema.type("number");
                break;
            case "Boolean":
            case "boolean":
                schema.type("boolean");
                break;
            case "List":
            case "Set":
            case "Collection":
                schema.type("array");
                // For collections, try to extract item type
                String itemType = extractGenericType(javaType);
                Schema<Object> itemSchema = new Schema<>();
                setSchemaType(itemSchema, itemType);
                schema.items(itemSchema);
                break;
            case "Date":
            case "LocalDate":
            case "LocalDateTime":
            case "Instant":
                schema.type("string");
                schema.format("date-time");
                break;
            default:
                schema.type("object");
                schema.description("Object of type: " + javaType);
                break;
        }
    }
    
    /**
     * Extract generic type from parameterized type (List<String> -> String).
     */
    private String extractGenericType(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start > 0 && end > start) {
            return type.substring(start + 1, end).trim();
        }
        return "Object";
    }
    
    /**
     * Check if field has validation annotations indicating it's required.
     */
    private boolean hasRequiredAnnotation(FieldDeclaration field) {
        return field.getAnnotations().stream()
            .anyMatch(ann -> {
                String annName = ann.getNameAsString();
                return annName.equals("NotNull") || 
                       annName.equals("NotEmpty") || 
                       annName.equals("NotBlank");
            });
    }
    
    /**
     * Check if class has getter method for field.
     */
    private boolean hasGetter(ClassOrInterfaceDeclaration classDecl, FieldDeclaration field) {
        // Simple heuristic: look for methods starting with "get" or "is"
        String fieldName = field.getVariable(0).getNameAsString();
        String getterName = "get" + capitalize(fieldName);
        String booleanGetterName = "is" + capitalize(fieldName);
        
        return classDecl.getMethods().stream()
            .anyMatch(method -> {
                String methodName = method.getNameAsString();
                return methodName.equals(getterName) || methodName.equals(booleanGetterName);
            });
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Extract class description from JavaDoc or annotations.
     */
    private String extractClassDescription(ClassOrInterfaceDeclaration classDecl, Path sourceFile) {
        // Check for @Schema annotation description
        Optional<String> schemaDesc = classDecl.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("Schema"))
            .map(ann -> extractAnnotationValue(ann.toString(), "description"))
            .filter(Objects::nonNull)
            .findFirst();
        
        if (schemaDesc.isPresent()) {
            return schemaDesc.get();
        }
        
        // Check for JavaDoc
        if (classDecl.getJavadoc().isPresent()) {
            return classDecl.getJavadoc().get().getDescription().toText().trim();
        }
        
        // Check if this appears to be generated code
        String fileName = sourceFile.toString();
        if (fileName.contains("generated-sources") || fileName.contains("target/generated")) {
            return "Generated DTO class: " + classDecl.getNameAsString();
        }
        
        return "DTO class: " + classDecl.getNameAsString();
    }
    
    /**
     * Extract field description from annotations or JavaDoc.
     */
    private String extractFieldDescription(FieldDeclaration field, String fieldName, String fieldType) {
        // Check for @Schema annotation description
        Optional<String> schemaDesc = field.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals("Schema"))
            .map(ann -> extractAnnotationValue(ann.toString(), "description"))
            .filter(Objects::nonNull)
            .findFirst();
        
        if (schemaDesc.isPresent()) {
            return schemaDesc.get();
        }
        
        // Check for JavaDoc
        if (field.getJavadoc().isPresent()) {
            return field.getJavadoc().get().getDescription().toText().trim();
        }
        
        return null; // No description found
    }
    
    /**
     * Simple annotation value extractor.
     */
    private String extractAnnotationValue(String annotationStr, String attributeName) {
        try {
            String pattern = attributeName + "\\s*=\\s*[\"']([^\"']*)[\"']";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(annotationStr);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // Ignore regex errors
        }
        return null;
    }
    
    /**
     * Create placeholder schema for missing DTO classes.
     */
    private Schema<?> createPlaceholderSchema(String className) {
        Schema<Object> schema = new Schema<>();
        schema.type("object");
        schema.description("DTO class '" + className + "' - Schema not available (generated source may be missing)");
        
        // Add a placeholder property to indicate the issue
        Map<String, Schema> properties = new HashMap<>();
        Schema<Object> placeholderProp = new Schema<>();
        placeholderProp.type("string");
        placeholderProp.description("This DTO schema is not available. " +
            "The class '" + className + "' may be generated code that needs to be built first.");
        properties.put("_schemaPlaceholder", placeholderProp);
        
        schema.properties(properties);
        return schema;
    }
    
    /**
     * Clear schema cache (useful for testing).
     */
    public void clearCache() {
        schemaCache.clear();
    }
    
    /**
     * Get cached schema count (useful for testing).
     */
    public int getCachedSchemaCount() {
        return schemaCache.size();
    }
}