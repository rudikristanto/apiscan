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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ApiScanCLI()).execute(args);
        System.exit(exitCode);
    }
    
    public enum OutputFormat {
        json, yaml
    }
}