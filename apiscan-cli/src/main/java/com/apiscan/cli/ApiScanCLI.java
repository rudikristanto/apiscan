package com.apiscan.cli;

import com.apiscan.core.framework.FrameworkDetector;
import com.apiscan.core.framework.FrameworkScanner;
import com.apiscan.core.model.ScanResult;
import com.apiscan.framework.spring.SpringFrameworkDetector;
import com.apiscan.framework.spring.SpringFrameworkScanner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Command(name = "apiscan", 
         mixinStandardHelpOptions = true,
         version = "apiscan 1.0.0",
         description = "Enterprise-grade API endpoint scanner for Java projects")
public class ApiScanCLI implements Callable<Integer> {
    
    @Parameters(index = "0", 
                description = "Path to the project to scan",
                defaultValue = ".")
    private String projectPath;
    
    @Option(names = {"-o", "--output"}, 
            description = "Output file path for OpenAPI specification")
    private String outputPath;
    
    @Option(names = {"-f", "--format"}, 
            description = "Output format: ${COMPLETION-CANDIDATES}",
            defaultValue = "yaml")
    private OutputFormat format;
    
    @Option(names = {"--framework"}, 
            description = "Force specific framework (auto-detect if not specified)")
    private String framework;
    
    @Option(names = {"-v", "--verbose"}, 
            description = "Enable verbose output")
    private boolean verbose;
    
    @Option(names = {"--summary"}, 
            description = "Show summary report",
            defaultValue = "true")
    private boolean showSummary;
    
    @Option(names = {"--microservices-output"}, 
            description = "Output mode for microservices: ${COMPLETION-CANDIDATES}",
            defaultValue = "combined")
    private MicroservicesOutputMode microservicesOutputMode;
    
    private final List<FrameworkDetector> detectors;
    private final List<FrameworkScanner> scanners;
    private final SwaggerCoreOpenApiGenerator openApiGenerator;
    private final ReportGenerator reportGenerator;
    
    public ApiScanCLI() {
        this.detectors = new ArrayList<>();
        this.scanners = new ArrayList<>();
        
        // Register framework support
        detectors.add(new SpringFrameworkDetector());
        scanners.add(new SpringFrameworkScanner());
        
        this.openApiGenerator = new SwaggerCoreOpenApiGenerator();
        this.reportGenerator = new ReportGenerator();
    }
    
    @Override
    public Integer call() throws Exception {
        Path path = Paths.get(projectPath).toAbsolutePath().normalize();
        
        System.out.println();
        System.out.println("=========================================================");
        System.out.println("|                   APISCAN v1.0.0                     |");
        System.out.println("|            Enterprise API Endpoint Scanner           |");
        System.out.println("=========================================================");
        System.out.println();
        System.out.println("[INFO] Analyzing project: " + path.getFileName());
        System.out.println("[INFO] Location: " + path);
        
        // Check if this is a microservices architecture
        if (isIndependentMicroservices(path)) {
            System.out.println("[INFO] Detected microservices architecture");
            return handleMicroservices(path);
        }
        
        // Single project or multi-module project (handled by scanner)
        return handleSingleProject(path);
    }
    
    private Integer handleSingleProject(Path path) throws Exception {
        // Detect framework
        String detectedFramework = detectFramework(path);
        if (detectedFramework == null) {
            System.out.println();
            System.out.println("[ERROR] No supported framework detected");
            System.out.println("        Supported frameworks: Spring MVC, Spring Boot");
            System.out.println("        Please ensure your project contains a valid pom.xml or build.gradle");
            return 1;
        }
        
        System.out.println("[INFO] Framework detected: " + detectedFramework);
        
        // Find appropriate scanner
        FrameworkScanner scanner = findScanner(detectedFramework);
        if (scanner == null) {
            System.out.println("[ERROR] No scanner available for framework: " + detectedFramework);
            return 1;
        }
        
        // Perform scan
        System.out.println("[INFO] Scanning for API endpoints...");
        long startTime = System.currentTimeMillis();
        ScanResult result = scanner.scan(path);
        long scanTime = System.currentTimeMillis() - startTime;
        
        // Print scan completion
        System.out.println("[SUCCESS] Scan completed in " + scanTime + "ms");
        System.out.println("[RESULT] Found " + result.getEndpoints().size() + " API endpoints");
        
        // Generate OpenAPI specification (core requirement from CLAUDE.md)
        System.out.println("[INFO] Generating OpenAPI specification...");
        String openApiSpec = openApiGenerator.generate(result, format);
        
        // Determine output file path
        Path outputFile;
        if (outputPath != null) {
            outputFile = Paths.get(outputPath);
        } else {
            // Auto-generate filename based on project name and format in current directory
            String projectName = path.getFileName().toString();
            String fileName = projectName + "-openapi." + format.toString().toLowerCase();
            outputFile = Paths.get(System.getProperty("user.dir")).resolve(fileName);
        }
        
        // Save OpenAPI specification to file
        openApiGenerator.saveToFile(openApiSpec, outputFile);
        System.out.println("[SUCCESS] OpenAPI specification saved: " + outputFile.toAbsolutePath());
        
        // Print spec to console if verbose mode is enabled
        if (verbose) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("OpenAPI Specification (" + format.toString().toUpperCase() + ")");
            System.out.println("=".repeat(60));
            System.out.println(openApiSpec);
        }
        
        // Show summary if requested
        if (showSummary) {
            reportGenerator.printSummary(result, scanTime);
        }
        
        return 0;
    }
    
    private Integer handleMicroservices(Path path) throws Exception {
        List<Path> microservices = findMicroservices(path);
        System.out.println("[INFO] Found " + microservices.size() + " microservices");
        
        Map<String, ScanResult> serviceResults = new HashMap<>();
        long totalStartTime = System.currentTimeMillis();
        
        // Scan each microservice
        for (Path servicePath : microservices) {
            String serviceName = servicePath.getFileName().toString();
            System.out.println();
            System.out.println("[INFO] Scanning microservice: " + serviceName);
            
            String detectedFramework = detectFramework(servicePath);
            if (detectedFramework == null) {
                System.out.println("[WARN] No framework detected for " + serviceName + ", skipping");
                continue;
            }
            
            FrameworkScanner scanner = findScanner(detectedFramework);
            if (scanner == null) {
                System.out.println("[WARN] No scanner available for " + serviceName + ", skipping");
                continue;
            }
            
            long startTime = System.currentTimeMillis();
            ScanResult result = scanner.scan(servicePath);
            long scanTime = System.currentTimeMillis() - startTime;
            
            serviceResults.put(serviceName, result);
            System.out.println("[SUCCESS] Found " + result.getEndpoints().size() + 
                             " endpoints in " + serviceName + " (" + scanTime + "ms)");
        }
        
        long totalScanTime = System.currentTimeMillis() - totalStartTime;
        
        if (serviceResults.isEmpty()) {
            System.out.println("[ERROR] No microservices could be scanned");
            return 1;
        }
        
        // Generate OpenAPI specifications based on output mode
        if (microservicesOutputMode == MicroservicesOutputMode.separate) {
            // Generate individual OpenAPI files for each microservice
            System.out.println();
            System.out.println("[INFO] Generating individual OpenAPI specifications...");
            
            for (Map.Entry<String, ScanResult> entry : serviceResults.entrySet()) {
                String serviceName = entry.getKey();
                ScanResult result = entry.getValue();
                
                String openApiSpec = openApiGenerator.generate(result, format);
                
                Path outputFile;
                if (outputPath != null) {
                    // If output path is specified, use it as directory
                    Path outputDir = Paths.get(outputPath);
                    Files.createDirectories(outputDir);
                    String fileName = serviceName + "-openapi." + format.toString().toLowerCase();
                    outputFile = outputDir.resolve(fileName);
                } else {
                    String fileName = serviceName + "-openapi." + format.toString().toLowerCase();
                    outputFile = Paths.get(System.getProperty("user.dir")).resolve(fileName);
                }
                
                openApiGenerator.saveToFile(openApiSpec, outputFile);
                System.out.println("[SUCCESS] " + serviceName + " OpenAPI saved: " + outputFile.toAbsolutePath());
            }
        } else {
            // Generate combined OpenAPI file (default)
            System.out.println();
            System.out.println("[INFO] Generating combined OpenAPI specification...");
            
            // Merge all results into one
            ScanResult combinedResult = new ScanResult();
            combinedResult.setProjectPath(path.toString());
            combinedResult.setFramework("Spring Microservices");
            
            int totalEndpoints = 0;
            int totalFiles = 0;
            
            for (Map.Entry<String, ScanResult> entry : serviceResults.entrySet()) {
                String serviceName = entry.getKey();
                ScanResult result = entry.getValue();
                
                // Prefix endpoints with service name for clarity
                for (var endpoint : result.getEndpoints()) {
                    // Optionally prefix the path with service name
                    // This helps distinguish endpoints from different services
                    combinedResult.getEndpoints().add(endpoint);
                }
                
                totalEndpoints += result.getEndpoints().size();
                totalFiles += result.getFilesScanned();
                
                // Merge errors
                for (String error : result.getErrors()) {
                    combinedResult.addError("[" + serviceName + "] " + error);
                }
            }
            
            combinedResult.setFilesScanned(totalFiles);
            combinedResult.setScanDurationMs(totalScanTime);
            
            String openApiSpec = openApiGenerator.generate(combinedResult, format);
            
            Path outputFile;
            if (outputPath != null) {
                outputFile = Paths.get(outputPath);
            } else {
                String projectName = path.getFileName().toString();
                String fileName = projectName + "-openapi." + format.toString().toLowerCase();
                outputFile = Paths.get(System.getProperty("user.dir")).resolve(fileName);
            }
            
            openApiGenerator.saveToFile(openApiSpec, outputFile);
            System.out.println("[SUCCESS] Combined OpenAPI specification saved: " + outputFile.toAbsolutePath());
            System.out.println("[RESULT] Total endpoints across all microservices: " + totalEndpoints);
            
            if (showSummary) {
                reportGenerator.printSummary(combinedResult, totalScanTime);
            }
        }
        
        return 0;
    }
    
    private String detectFramework(Path path) {
        if (framework != null) {
            // Framework explicitly specified
            return framework;
        }
        
        // Auto-detect framework
        return detectors.stream()
            .filter(detector -> detector.detect(path))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .map(FrameworkDetector::getFrameworkName)
            .findFirst()
            .orElse(null);
    }
    
    private FrameworkScanner findScanner(String frameworkName) {
        return scanners.stream()
            .filter(scanner -> scanner.supports(frameworkName))
            .findFirst()
            .orElse(null);
    }
    
    private boolean isIndependentMicroservices(Path projectPath) {
        // Check if this directory has multiple subdirectories with build files but no parent build file
        Path parentPom = projectPath.resolve("pom.xml");
        Path parentGradle = projectPath.resolve("build.gradle");
        Path parentGradleKts = projectPath.resolve("build.gradle.kts");
        
        // If there's a parent build file, it's not independent microservices
        if (Files.exists(parentPom) || Files.exists(parentGradle) || Files.exists(parentGradleKts)) {
            return false;
        }
        
        try (Stream<Path> paths = Files.list(projectPath)) {
            List<Path> serviceDirectories = paths
                .filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .filter(dir -> !dir.getFileName().toString().equals("docs"))
                .filter(dir -> !dir.getFileName().toString().equals("target"))
                .filter(dir -> !dir.getFileName().toString().equals("build"))
                .filter(dir -> !dir.getFileName().toString().equals("postman_collection"))
                .filter(this::hasBuildFile)
                .collect(Collectors.toList());
            
            // Consider it microservices if there are at least 2 service directories
            return serviceDirectories.size() >= 2;
        } catch (IOException e) {
            return false;
        }
    }
    
    private List<Path> findMicroservices(Path projectPath) {
        try (Stream<Path> paths = Files.list(projectPath)) {
            return paths
                .filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .filter(dir -> !dir.getFileName().toString().equals("docs"))
                .filter(dir -> !dir.getFileName().toString().equals("target"))
                .filter(dir -> !dir.getFileName().toString().equals("build"))
                .filter(dir -> !dir.getFileName().toString().equals("postman_collection"))
                .filter(this::hasBuildFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }
    
    private boolean hasBuildFile(Path directory) {
        return Files.exists(directory.resolve("pom.xml")) ||
               Files.exists(directory.resolve("build.gradle")) ||
               Files.exists(directory.resolve("build.gradle.kts"));
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiScanCLI()).execute(args);
        System.exit(exitCode);
    }
    
    public enum OutputFormat {
        json, yaml
    }
    
    public enum MicroservicesOutputMode {
        combined,    // Single combined OpenAPI file (default)
        separate     // Individual OpenAPI file per microservice
    }
}