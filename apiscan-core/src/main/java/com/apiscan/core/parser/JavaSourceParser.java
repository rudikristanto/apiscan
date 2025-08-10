package com.apiscan.core.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaSourceParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaSourceParser.class);
    private final JavaParser javaParser;
    
    public JavaSourceParser(Path projectPath) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        
        // Add project source directories to type solver
        findSourceDirectories(projectPath).forEach(srcPath -> {
            if (Files.exists(srcPath)) {
                typeSolver.add(new JavaParserTypeSolver(srcPath));
            }
        });
        
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        this.javaParser = new JavaParser();
        this.javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }
    
    public Optional<CompilationUnit> parseFile(Path filePath) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(filePath);
            if (result.isSuccessful()) {
                return result.getResult();
            } else {
                logger.warn("Failed to parse file {}: {}", filePath, 
                    result.getProblems().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ")));
            }
        } catch (IOException e) {
            logger.error("Error reading file {}: {}", filePath, e.getMessage());
        }
        return Optional.empty();
    }
    
    public List<ClassOrInterfaceDeclaration> findClasses(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class);
    }
    
    public List<MethodDeclaration> findMethods(ClassOrInterfaceDeclaration clazz) {
        return clazz.getMethods();
    }
    
    public List<AnnotationExpr> getAnnotations(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotations();
    }
    
    public List<AnnotationExpr> getAnnotations(MethodDeclaration method) {
        return method.getAnnotations();
    }
    
    public Optional<AnnotationExpr> findAnnotation(List<AnnotationExpr> annotations, String name) {
        return annotations.stream()
            .filter(ann -> ann.getNameAsString().equals(name) || 
                          ann.getNameAsString().endsWith("." + name))
            .findFirst();
    }
    
    private List<Path> findSourceDirectories(Path projectPath) {
        List<Path> sourceDirs = new ArrayList<>();
        sourceDirs.add(projectPath.resolve("src/main/java"));
        sourceDirs.add(projectPath.resolve("src/test/java"));
        
        // Look for Maven multi-module structure
        try (Stream<Path> paths = Files.list(projectPath)) {
            paths.filter(Files::isDirectory)
                .filter(path -> !path.getFileName().toString().startsWith("."))
                .forEach(modulePath -> {
                    Path srcPath = modulePath.resolve("src/main/java");
                    if (Files.exists(srcPath)) {
                        sourceDirs.add(srcPath);
                    }
                });
        } catch (IOException e) {
            logger.debug("Error scanning for source directories: {}", e.getMessage());
        }
        
        return sourceDirs;
    }
}