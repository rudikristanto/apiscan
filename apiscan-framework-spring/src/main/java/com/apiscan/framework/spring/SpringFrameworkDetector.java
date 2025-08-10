package com.apiscan.framework.spring;

import com.apiscan.core.framework.FrameworkDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SpringFrameworkDetector implements FrameworkDetector {
    private static final Logger logger = LoggerFactory.getLogger(SpringFrameworkDetector.class);
    
    @Override
    public boolean detect(Path projectPath) {
        // Check for pom.xml with Spring dependencies
        Path pomPath = projectPath.resolve("pom.xml");
        if (Files.exists(pomPath)) {
            try {
                String pomContent = Files.readString(pomPath);
                if (pomContent.contains("spring-boot-starter-web") ||
                    pomContent.contains("spring-webmvc") ||
                    pomContent.contains("spring-boot-starter")) {
                    logger.info("Detected Spring framework via pom.xml");
                    return true;
                }
            } catch (IOException e) {
                logger.error("Error reading pom.xml: {}", e.getMessage());
            }
        }
        
        // Check for build.gradle with Spring dependencies
        Path gradlePath = projectPath.resolve("build.gradle");
        if (Files.exists(gradlePath)) {
            try {
                String gradleContent = Files.readString(gradlePath);
                if (gradleContent.contains("spring-boot-starter-web") ||
                    gradleContent.contains("spring-webmvc")) {
                    logger.info("Detected Spring framework via build.gradle");
                    return true;
                }
            } catch (IOException e) {
                logger.error("Error reading build.gradle: {}", e.getMessage());
            }
        }
        
        // Check for Spring configuration files
        if (hasSpringConfigFiles(projectPath)) {
            logger.info("Detected Spring framework via configuration files");
            return true;
        }
        
        // Check for @RestController or @Controller annotations in Java files
        if (hasSpringAnnotations(projectPath)) {
            logger.info("Detected Spring framework via annotations");
            return true;
        }
        
        return false;
    }
    
    @Override
    public String getFrameworkName() {
        return "Spring";
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority for Spring
    }
    
    private boolean hasSpringConfigFiles(Path projectPath) {
        Path resourcesPath = projectPath.resolve("src/main/resources");
        if (!Files.exists(resourcesPath)) {
            return false;
        }
        
        try (Stream<Path> paths = Files.walk(resourcesPath, 2)) {
            return paths.anyMatch(path -> {
                String fileName = path.getFileName().toString();
                return fileName.equals("application.properties") ||
                       fileName.equals("application.yml") ||
                       fileName.equals("application.yaml") ||
                       fileName.startsWith("application-");
            });
        } catch (IOException e) {
            logger.error("Error checking for Spring config files: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean hasSpringAnnotations(Path projectPath) {
        Path srcPath = projectPath.resolve("src/main/java");
        if (!Files.exists(srcPath)) {
            return false;
        }
        
        try (Stream<Path> paths = Files.walk(srcPath)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .limit(10) // Check only first 10 Java files for performance
                .anyMatch(this::containsSpringAnnotations);
        } catch (IOException e) {
            logger.error("Error checking for Spring annotations: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean containsSpringAnnotations(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            return content.contains("@RestController") ||
                   content.contains("@Controller") ||
                   content.contains("@SpringBootApplication") ||
                   content.contains("@RequestMapping");
        } catch (IOException e) {
            return false;
        }
    }
}