package com.apiscan.core.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JavaSourceParserTest {
    
    private JavaSourceParser parser;
    
    @Test
    void testParseSimpleJavaFile(@TempDir Path tempDir) throws IOException {
        // Create parser
        parser = new JavaSourceParser(tempDir);
        
        // Create a simple Java class
        String javaContent = """
            package com.example;
            
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class TestController {
                public String test() {
                    return "test";
                }
            }
            """;
        
        Path javaFile = createJavaFile(tempDir, "com/example/TestController.java", javaContent);
        
        // Parse the file
        Optional<CompilationUnit> result = parser.parseFile(javaFile);
        
        // Verify parsing
        assertTrue(result.isPresent(), "Should successfully parse Java file");
        
        CompilationUnit cu = result.get();
        List<ClassOrInterfaceDeclaration> classes = parser.findClasses(cu);
        
        assertEquals(1, classes.size(), "Should find one class");
        assertEquals("TestController", classes.get(0).getNameAsString(), "Should find correct class name");
        assertFalse(classes.get(0).isInterface(), "Should be a class, not interface");
    }
    
    @Test
    void testParseInterfaceFile(@TempDir Path tempDir) throws IOException {
        parser = new JavaSourceParser(tempDir);
        
        String interfaceContent = """
            package com.example.api;
            
            import org.springframework.web.bind.annotation.GetMapping;
            
            public interface UsersApi {
                @GetMapping("/users")
                String getUsers();
            }
            """;
        
        Path javaFile = createJavaFile(tempDir, "com/example/api/UsersApi.java", interfaceContent);
        
        Optional<CompilationUnit> result = parser.parseFile(javaFile);
        
        assertTrue(result.isPresent(), "Should successfully parse interface file");
        
        CompilationUnit cu = result.get();
        List<ClassOrInterfaceDeclaration> classes = parser.findClasses(cu);
        
        assertEquals(1, classes.size(), "Should find one interface");
        assertEquals("UsersApi", classes.get(0).getNameAsString(), "Should find correct interface name");
        assertTrue(classes.get(0).isInterface(), "Should be an interface");
    }
    
    @Test
    void testParseFileWithSyntaxError(@TempDir Path tempDir) throws IOException {
        parser = new JavaSourceParser(tempDir);
        
        String invalidJavaContent = """
            package com.example;
            
            public class InvalidClass {
                public String test() {
                    return "test"  // Missing semicolon
                }
            """; // Missing closing brace
        
        Path javaFile = createJavaFile(tempDir, "com/example/InvalidClass.java", invalidJavaContent);
        
        Optional<CompilationUnit> result = parser.parseFile(javaFile);
        
        assertFalse(result.isPresent(), "Should fail to parse invalid Java file");
    }
    
    @Test
    void testParseNonExistentFile(@TempDir Path tempDir) {
        parser = new JavaSourceParser(tempDir);
        
        Path nonExistentFile = tempDir.resolve("non-existent.java");
        
        Optional<CompilationUnit> result = parser.parseFile(nonExistentFile);
        
        assertFalse(result.isPresent(), "Should return empty for non-existent file");
    }
    
    @Test
    void testGetProjectPath(@TempDir Path tempDir) {
        parser = new JavaSourceParser(tempDir);
        
        assertEquals(tempDir, parser.getProjectPath(), "Should return correct project path");
    }
    
    private Path createJavaFile(Path baseDir, String relativePath, String content) throws IOException {
        Path srcDir = baseDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        
        Path javaFile = srcDir.resolve(relativePath);
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, content);
        
        return javaFile;
    }
}