package com.apiscan.cli;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves DTO class schemas by parsing generated or manual DTO classes.
 * Handles both generated OpenAPI DTOs and manual DTOs.
 */
public class DtoSchemaResolver {
    
    private static final Logger logger = LoggerFactory.getLogger(DtoSchemaResolver.class);
    
    private final Map<String, Schema<?>> schemaCache = new HashMap<>();
    private final Map<String, Boolean> dtoClassCache = new HashMap<>();
    private final Set<String> resolving = new HashSet<>(); // Track currently resolving schemas
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
        
        // Sanitize the class name early to ensure consistency
        String sanitizedClassName = sanitizeSchemaName(className);
        
        // Check cache first using sanitized name
        if (schemaCache.containsKey(sanitizedClassName)) {
            return schemaCache.get(sanitizedClassName);
        }
        
        // Prevent circular resolution using original name (for file lookup)
        if (resolving.contains(className)) {
            logger.debug("Circular reference detected for class '{}', using placeholder", className);
            Schema<?> placeholder = createPlaceholderSchema(sanitizedClassName);
            schemaCache.put(sanitizedClassName, placeholder);
            return placeholder;
        }
        
        resolving.add(className);
        
        try {
            Schema<?> schema = null;
            
            // Try to find and parse the DTO class using original name
            Optional<Path> dtoFile = findDtoFile(className);
            if (dtoFile.isPresent()) {
                logger.debug("Found DTO file for '{}' at: {}", className, dtoFile.get());
                schema = parseSchemaFromFile(dtoFile.get(), className);
            } else {
                logger.debug("No DTO file found for class '{}'", className);
            }
            
            // If parsing failed or file not found, create placeholder
            if (schema == null) {
                schema = createPlaceholderSchema(sanitizedClassName);
                logger.debug("Could not find or parse DTO class '{}', using placeholder schema", className);
            } else {
                logger.debug("Successfully resolved schema for DTO class '{}' as '{}'", className, sanitizedClassName);
            }
            
            // Cache the result using sanitized name
            schemaCache.put(sanitizedClassName, schema);
            return schema;
        } finally {
            resolving.remove(className);
        }
    }
    
    /**
     * Find DTO file in common locations within the project.
     * Supports both single-module and multi-module Maven projects.
     */
    private Optional<Path> findDtoFile(String className) {
        if (projectPath == null || projectPath.trim().isEmpty()) {
            logger.debug("Project path is null or empty, cannot search for DTO '{}'", className);
            return Optional.empty();
        }
        
        try {
            List<Path> allMatches = new ArrayList<>();
            
            // First, try to find in current module
            Optional<Path> currentModuleResult = findDtoInModule(Paths.get(projectPath), className);
            if (currentModuleResult.isPresent()) {
                allMatches.add(currentModuleResult.get());
            }
            
            // For multi-module projects, check both current directory for sub-modules and parent directory for sibling modules
            Path currentDir = Paths.get(projectPath);
            
            // First, check current directory for sub-modules (handles parent directory with multiple modules)
            if (Files.exists(currentDir)) {
                List<Path> subModuleMatches = findAllMatchesInMultiModuleProject(currentDir, className);
                allMatches.addAll(subModuleMatches);
            }
            
            // Then, check parent directory for sibling modules (handles single module with siblings)
            Path parentDir = currentDir.getParent();
            if (parentDir != null && Files.exists(parentDir) && !parentDir.equals(currentDir)) {
                List<Path> siblingModuleMatches = findAllMatchesInMultiModuleProject(parentDir, className);
                allMatches.addAll(siblingModuleMatches);
            }
            
            if (allMatches.isEmpty()) {
                return Optional.empty();
            }
            
            if (allMatches.size() > 1) {
                logger.info("MULTI-MATCH: Found {} total matches for '{}', applying prioritization", allMatches.size(), className);
                return prioritizeDtoPackage(allMatches, className + ".java");
            }
            
            return Optional.of(allMatches.get(0));
        } catch (Exception e) {
            logger.debug("Error searching for DTO '{}': {}", className, e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Search for DTO in a single module directory.
     */
    private Optional<Path> findDtoInModule(Path moduleRoot, String className) {
        // Common locations for DTO files within a module
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
                Path basePath = moduleRoot.resolve(searchPath);
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
     * Find all matches for a class across all modules in a multi-module Maven project.
     */
    private List<Path> findAllMatchesInMultiModuleProject(Path parentDir, String className) {
        try (Stream<Path> modules = Files.list(parentDir)) {
            List<Path> potentialModules = modules
                .filter(Files::isDirectory)
                .filter(this::isPotentialMavenModule)
                .collect(Collectors.toList());
                
            logger.debug("Found {} potential Maven modules in {}: {}", 
                potentialModules.size(), parentDir,
                potentialModules.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList()));
                
            // Collect all matches from all modules
            return potentialModules.stream()
                .map(moduleDir -> {
                    logger.debug("Searching for '{}' in module: {}", className, moduleDir.getFileName());
                    return findDtoInModule(moduleDir, className);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.debug("Error scanning multi-module project for DTO '{}': {}", className, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Search for DTO in multi-module Maven project structure.
     * Looks for sibling modules that might contain model classes.
     * @deprecated Use findAllMatchesInMultiModuleProject for better prioritization
     */
    @Deprecated
    private Optional<Path> findDtoInMultiModuleProject(Path parentDir, String className) {
        try (Stream<Path> modules = Files.list(parentDir)) {
            List<Path> potentialModules = modules
                .filter(Files::isDirectory)
                .filter(this::isPotentialMavenModule)
                .collect(Collectors.toList());
                
            logger.debug("Found {} potential Maven modules in {}: {}", 
                potentialModules.size(), parentDir,
                potentialModules.stream().map(p -> p.getFileName().toString()).collect(Collectors.toList()));
                
            // Collect all matches from all modules instead of stopping at first match
            List<Path> allMatches = potentialModules.stream()
                .map(moduleDir -> {
                    logger.debug("Searching for '{}' in module: {}", className, moduleDir.getFileName());
                    return findDtoInModule(moduleDir, className);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
                
            if (allMatches.isEmpty()) {
                return Optional.empty();
            }
            
            if (allMatches.size() > 1) {
                logger.debug("Found {} matches for '{}' across modules, applying prioritization", allMatches.size(), className);
                return prioritizeDtoPackage(allMatches, className + ".java");
            }
            
            return Optional.of(allMatches.get(0));
        } catch (IOException e) {
            logger.debug("Error scanning multi-module project for DTO '{}': {}", className, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Check if a directory is likely a Maven module.
     */
    private boolean isPotentialMavenModule(Path dir) {
        // Check for common indicators of Maven modules
        return Files.exists(dir.resolve("pom.xml")) ||
               Files.exists(dir.resolve("src/main/java")) ||
               dir.getFileName().toString().contains("model") ||
               dir.getFileName().toString().contains("api") ||
               dir.getFileName().toString().contains("dto") ||
               dir.getFileName().toString().contains("entity") ||
               dir.getFileName().toString().contains("service") ||
               dir.getFileName().toString().contains("core");
    }
    
    /**
     * Parse a Java file using JavaParser.
     */
    private Optional<CompilationUnit> parseJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            return parser.parse(javaFile).getResult();
        } catch (Exception e) {
            logger.debug("Error parsing Java file '{}': {}", javaFile, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Recursively search for a file by name in directory tree.
     * Prioritizes DTO-appropriate packages over JPA entity packages.
     */
    protected Optional<Path> findFileRecursively(Path dir, String fileName) {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> allMatches = walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(fileName))
                .collect(Collectors.toList());
            
            if (allMatches.isEmpty()) {
                return Optional.empty();
            }
            
            // If multiple matches found, prioritize DTO-appropriate packages
            if (allMatches.size() > 1) {
                return prioritizeDtoPackage(allMatches, fileName);
            }
            
            return Optional.of(allMatches.get(0));
        } catch (IOException e) {
            logger.debug("Error walking directory '{}': {}", dir, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Prioritize DTO-appropriate packages when multiple class files are found.
     * Prefers DTO/API layer classes over JPA entity classes.
     */
    protected Optional<Path> prioritizeDtoPackage(List<Path> matches, String fileName) {
        logger.debug("Multiple matches found for '{}': {}", fileName, 
            matches.stream().map(Path::toString).collect(Collectors.toList()));
        // Define package priority (higher priority first)
        List<String> packagePriorities = Arrays.asList(
            // DTO/API layer packages (highest priority)
            "sm-shop-model",        // Shopizer DTO module (highest priority)
            "shop.model",           // Shopizer DTO layer
            "api.model",
            "rest.model", 
            "dto",
            "dtos",
            "generated",            // Generated DTOs
            "openapi",
            
            // Domain model packages (medium priority - can be DTOs in microservices)
            "notification.domain",  // Microservice domain models
            "account.domain",       // Microservice domain models
            "statistics.domain",    // Microservice domain models
            ".domain",              // General domain models (may be DTOs)
            
            // JPA/Core entity packages (lower priority)
            "sm-core-model",        // Shopizer JPA entity module (lower priority)
            "core.model",           // Shopizer JPA entity layer  
            "entity",
            "entities",
            "persistence"
        );
        
        // Find best match based on package priority
        for (String priorityPackage : packagePriorities) {
            Optional<Path> priorityMatch = matches.stream()
                .filter(path -> path.toString().contains(priorityPackage))
                .findFirst();
            if (priorityMatch.isPresent()) {
                logger.info("PRIORITIZATION: Selected '{}' from package '{}' over {} other matches: {}", 
                    fileName, priorityPackage, matches.size() - 1,
                    matches.stream().filter(p -> !p.equals(priorityMatch.get())).map(Path::toString).collect(Collectors.toList()));
                return priorityMatch;
            }
        }
        
        // If no priority match found, return first match
        logger.debug("No priority package match found for '{}', using first match", fileName);
        return Optional.of(matches.get(0));
    }
    
    /**
     * Parse schema from DTO file using JavaParser.
     */
    private Schema<?> parseSchemaFromFile(Path file, String className) {
        try {
            logger.debug("Parsing schema from file: {} for class: {}", file, className);
            String content = Files.readString(file);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElse(null);
            
            if (cu == null) {
                logger.debug("Failed to parse compilation unit from file: {}", file);
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
        String className = classDecl.getNameAsString();
        logger.debug("Parsing schema for class: {}", className);
        
        Schema<Object> schema = new Schema<>();
        schema.type("object");
        
        // Set description
        String description = extractClassDescription(classDecl, sourceFile);
        schema.description(description);
        
        // Parse fields including inherited ones
        Map<String, Schema> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        // First, parse inherited fields from parent classes
        try {
            parseInheritedFields(classDecl, properties, required);
            logger.debug("After inheritance parsing for '{}': {} properties, {} required", className, properties.size(), required.size());
        } catch (Exception e) {
            logger.warn("Error parsing inherited fields for class '{}': {}", className, e.getMessage(), e);
        }
        
        // Then parse fields from the current class
        int directFieldsCount = 0;
        for (FieldDeclaration field : classDecl.getFields()) {
            if (shouldIncludeField(field, classDecl)) {
                parseFieldSchema(field, properties, required);
                directFieldsCount++;
            }
        }
        logger.debug("Parsed {} direct fields for class '{}'. Total properties: {}", directFieldsCount, className, properties.size());
        
        if (!properties.isEmpty()) {
            schema.properties(properties);
        }
        
        if (!required.isEmpty()) {
            // Remove duplicates from required fields list
            List<String> uniqueRequired = new ArrayList<>(new LinkedHashSet<>(required));
            schema.required(uniqueRequired);
        }
        
        // Special logging for problematic classes
        if (className.equals("PersistableGroup") || className.equals("PersistableCustomerAttribute")) {
            logger.info("SPECIAL DEBUG for {}: {} total properties, {} required fields", className, properties.size(), required.size());
            properties.forEach((k, v) -> logger.info("  Property: {} -> {}", k, v.getType()));
        }
        
        return schema;
    }
    
    /**
     * Parse fields from parent classes (inheritance).
     */
    private void parseInheritedFields(ClassOrInterfaceDeclaration classDecl, Map<String, Schema> properties, List<String> required) {
        parseInheritedFields(classDecl, properties, required, new HashSet<>());
    }
    
    /**
     * Parse fields from parent classes (inheritance) with recursion protection.
     */
    private void parseInheritedFields(ClassOrInterfaceDeclaration classDecl, Map<String, Schema> properties, List<String> required, Set<String> visitedClasses) {
        // Limit inheritance depth to prevent infinite recursion
        if (visitedClasses.size() > 10) {
            logger.debug("Maximum inheritance depth reached, stopping recursion");
            return;
        }
        if (classDecl.getExtendedTypes().isEmpty()) {
            return;
        }
        
        for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
            String parentClassName = extendedType.getNameAsString();
            
            // Prevent infinite recursion
            if (visitedClasses.contains(parentClassName)) {
                logger.debug("Avoiding infinite recursion for parent class: {}", parentClassName);
                continue;
            }
            visitedClasses.add(parentClassName);
            
            // Try to find and parse the parent class
            Optional<Path> parentFile = findDtoFile(parentClassName);
            if (parentFile.isPresent()) {
                try {
                    Optional<CompilationUnit> parentCu = parseJavaFile(parentFile.get());
                    if (parentCu.isPresent()) {
                        Optional<ClassOrInterfaceDeclaration> parentClass = parentCu.get()
                            .findAll(ClassOrInterfaceDeclaration.class)
                            .stream()
                            .filter(cd -> cd.getNameAsString().equals(parentClassName))
                            .findFirst();
                        
                        if (parentClass.isPresent()) {
                            // Recursively parse inherited fields from the parent's parents
                            parseInheritedFields(parentClass.get(), properties, required, visitedClasses);
                            
                            // Parse fields from this parent class
                            for (FieldDeclaration field : parentClass.get().getFields()) {
                                if (shouldIncludeField(field, parentClass.get())) {
                                    parseFieldSchema(field, properties, required);
                                }
                            }
                            
                            logger.debug("Successfully parsed inherited fields from parent class: {}", parentClassName);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not parse parent class '{}': {}", parentClassName, e.getMessage());
                }
            } else {
                logger.debug("Could not find parent class file for: {}", parentClassName);
            }
        }
    }
    
    /**
     * Parse schema for individual field.
     * Note: Field filtering (static fields, @ApiIgnore, etc.) is handled in shouldIncludeField()
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
                // Sanitize the class name for the reference
                String sanitizedClassName = sanitizeSchemaName(simpleClassName);
                
                // Only create reference without immediate resolution to prevent circular references
                fieldSchema.$ref("#/components/schemas/" + sanitizedClassName);
                
                // Ensure the referenced schema will be resolved later (add to a queue if not already resolving)
                if (!resolving.contains(simpleClassName) && !schemaCache.containsKey(simpleClassName)) {
                    logger.debug("Marking schema '{}' for deferred resolution", simpleClassName);
                    // This will be resolved when getAllResolvedSchemas() is called
                }
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
               type.equals("Set") || type.equals("Collection") ||
               type.startsWith("java.util.List<") || type.startsWith("java.util.Set<") ||
               type.startsWith("java.util.Collection<") || type.equals("java.util.List") ||
               type.equals("java.util.Set") || type.equals("java.util.Collection");
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
        // Check cache first
        if (dtoClassCache.containsKey(className)) {
            return dtoClassCache.get(className);
        }
        
        // Check if we've already resolved this schema using sanitized name
        String sanitizedClassName = sanitizeSchemaName(className);
        if (schemaCache.containsKey(sanitizedClassName)) {
            dtoClassCache.put(className, true);
            return true;
        }
        
        // Common DTO naming patterns
        boolean matchesPattern = className.endsWith("Dto") || 
               className.endsWith("DTO") || 
               className.endsWith("Response") || 
               className.endsWith("Request") || 
               className.endsWith("Entity") ||
               className.endsWith("Model") ||
               className.startsWith("Persistable") ||  // Shopizer pattern
               className.startsWith("Readable") ||     // Shopizer pattern
               className.contains("Customer") ||
               className.contains("Group") ||
               className.contains("Attribute");
        
        if (matchesPattern) {
            dtoClassCache.put(className, true);
            return true;
        }
        
        // Check if the class file exists in the project (more comprehensive but expensive check)
        Optional<Path> dtoFile = findDtoFile(className);
        boolean result = dtoFile.isPresent();
        dtoClassCache.put(className, result);
        return result;
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
     * Extract generic type from parameterized type (List<String> -> String, java.util.List<Order> -> Order).
     */
    private String extractGenericType(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        if (start > 0 && end > start) {
            String genericType = type.substring(start + 1, end).trim();
            // Handle fully qualified generic types (e.g., java.util.List<com.test.Order> -> Order)
            return extractSimpleClassName(genericType);
        }
        return "Object";
    }
    
    /**
     * Check if field is marked with @ApiIgnore annotation.
     */
    private boolean isApiIgnoredField(FieldDeclaration field) {
        return field.getAnnotations().stream()
            .anyMatch(ann -> {
                String annName = ann.getNameAsString();
                // Handle fully qualified names
                String simpleName = annName.contains(".") ? 
                    annName.substring(annName.lastIndexOf('.') + 1) : annName;
                return simpleName.equals("ApiIgnore") || 
                       simpleName.equals("JsonIgnore") ||
                       simpleName.equals("Hidden");
            });
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
     * Check if a field should be included in the schema.
     * Excludes static fields, final fields, and fields with problematic names.
     */
    private boolean shouldIncludeField(FieldDeclaration field, ClassOrInterfaceDeclaration classDecl) {
        // Skip fields marked with @ApiIgnore
        if (isApiIgnoredField(field)) {
            logger.debug("Skipping field with @ApiIgnore annotation");
            return false;
        }
        
        // Skip static fields (they're not part of instance data)
        if (field.isStatic()) {
            logger.debug("Skipping static field: {}", field.getVariable(0).getNameAsString());
            return false;
        }
        
        // Skip serialVersionUID (common Java serialization field)
        String fieldName = field.getVariable(0).getNameAsString();
        if ("serialVersionUID".equals(fieldName)) {
            logger.debug("Skipping serialVersionUID field");
            return false;
        }
        
        // Skip fields with problematic names that shouldn't be in schemas
        if ("DEFAULT_STRING_COLLATOR".equals(fieldName) || 
            fieldName.startsWith("DEFAULT_") ||
            fieldName.contains("COLLATOR")) {
            logger.debug("Skipping problematic field: {}", fieldName);
            return false;
        }
        
        // Only include fields that are public or have getters
        return field.isPublic() || hasGetter(classDecl, field);
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
     * Sanitize schema name to comply with OpenAPI specification.
     * Component names can only contain A-Z a-z 0-9 - . _
     */
    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null || schemaName.trim().isEmpty()) {
            return "UnknownSchema";
        }
        
        // Handle common problematic patterns
        if (schemaName.equals("?")) {
            return "UnknownType";
        }
        
        if (schemaName.equals("byte[]")) {
            return "ByteArray";
        }
        
        // Handle generic types like List<String>, Set<SomeClass>
        if (schemaName.contains("<") && schemaName.contains(">")) {
            // For now, just use the base type and ignore generics
            String baseType = schemaName.substring(0, schemaName.indexOf("<"));
            return sanitizeSchemaName(baseType);
        }
        
        if (schemaName.endsWith("[]")) {
            String baseType = schemaName.substring(0, schemaName.length() - 2);
            return sanitizeSchemaName(baseType) + "Array";
        }
        
        // Replace invalid characters with underscores
        String sanitized = schemaName.replaceAll("[^A-Za-z0-9._-]", "_");
        
        // Ensure it starts with a letter or underscore
        if (!sanitized.matches("^[A-Za-z_].*")) {
            sanitized = "Schema_" + sanitized;
        }
        
        // Remove consecutive underscores
        sanitized = sanitized.replaceAll("_{2,}", "_");
        
        // Remove trailing underscores
        sanitized = sanitized.replaceAll("_+$", "");
        
        return sanitized;
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
    
    /**
     * Get all resolved schemas. This method ensures all referenced schemas are resolved.
     * It maintains a depth limit to prevent excessive recursive resolution.
     */
    public Map<String, Schema<?>> getAllResolvedSchemas() {
        return getAllResolvedSchemas(7); // Increased default depth for complex schemas
    }
    
    /**
     * Get all resolved schemas with specified maximum resolution depth.
     */
    public Map<String, Schema<?>> getAllResolvedSchemas(int maxDepth) {
        Map<String, Schema<?>> result = new HashMap<>(schemaCache);
        Set<String> processedRefs = new HashSet<>();
        
        for (int depth = 0; depth < maxDepth; depth++) {
            Set<String> refsToResolve = new HashSet<>();
            
            // Find all $ref references in current schemas
            for (Schema<?> schema : result.values()) {
                findReferencedSchemas(schema, refsToResolve);
            }
            
            // Remove already processed references
            refsToResolve.removeAll(processedRefs);
            
            if (refsToResolve.isEmpty()) {
                break; // No more references to resolve
            }
            
            // Resolve each reference
            for (String ref : refsToResolve) {
                String sanitizedRef = sanitizeSchemaName(ref);
                if (!result.containsKey(sanitizedRef)) {
                    logger.debug("Resolving referenced schema at depth {}: {} -> {}", depth, ref, sanitizedRef);
                    Schema<?> resolvedSchema = resolveSchema(ref);
                    result.put(sanitizedRef, resolvedSchema);
                }
                processedRefs.add(ref);
            }
        }
        
        logger.debug("Resolved {} total schemas with max depth {}", result.size(), maxDepth);
        return result;
    }
    
    /**
     * Find all schema references in a schema and its properties.
     */
    private void findReferencedSchemas(Schema<?> schema, Set<String> refs) {
        if (schema == null) return;
        
        // Check direct $ref
        if (schema.get$ref() != null) {
            String ref = extractSchemaNameFromRef(schema.get$ref());
            if (ref != null) {
                refs.add(ref);
            }
        }
        
        // Check properties recursively
        if (schema.getProperties() != null) {
            for (Schema<?> propSchema : schema.getProperties().values()) {
                findReferencedSchemas(propSchema, refs);
            }
        }
        
        // Check array items
        if (schema.getItems() != null) {
            findReferencedSchemas(schema.getItems(), refs);
        }
        
        // Check additionalProperties if it's a schema
        if (schema.getAdditionalProperties() instanceof Schema) {
            findReferencedSchemas((Schema<?>) schema.getAdditionalProperties(), refs);
        }
        
        // Check allOf, oneOf, anyOf combinations
        if (schema.getAllOf() != null) {
            for (Schema<?> subSchema : schema.getAllOf()) {
                findReferencedSchemas(subSchema, refs);
            }
        }
        if (schema.getOneOf() != null) {
            for (Schema<?> subSchema : schema.getOneOf()) {
                findReferencedSchemas(subSchema, refs);
            }
        }
        if (schema.getAnyOf() != null) {
            for (Schema<?> subSchema : schema.getAnyOf()) {
                findReferencedSchemas(subSchema, refs);
            }
        }
    }
    
    /**
     * Extract schema name from $ref URL (#/components/schemas/SchemaName -> SchemaName).
     */
    private String extractSchemaNameFromRef(String ref) {
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            return ref.substring("#/components/schemas/".length());
        }
        return null;
    }
}