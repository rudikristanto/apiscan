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
    private final OpenApiGenerator openApiGenerator;
    private final ReportGenerator reportGenerator;
    
    public ApiScanCLI() {
        this.detectors = new ArrayList<>();
        this.scanners = new ArrayList<>();
        
        // Register framework support
        detectors.add(new SpringFrameworkDetector());
        scanners.add(new SpringFrameworkScanner());
        
        this.openApiGenerator = new OpenApiGenerator();
        this.reportGenerator = new ReportGenerator();
    }
    
    @Override
    public Integer call() throws Exception {
        Path path = Paths.get(projectPath).toAbsolutePath().normalize();
        
        System.out.println("🔍 API Scanner - Analyzing project: " + path);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Detect framework
        String detectedFramework = detectFramework(path);
        if (detectedFramework == null) {
            System.err.println("❌ No supported framework detected in the project");
            System.err.println("   Supported frameworks: Spring MVC, Spring Boot");
            return 1;
        }
        
        System.out.println("✅ Detected framework: " + detectedFramework);
        
        // Find appropriate scanner
        FrameworkScanner scanner = findScanner(detectedFramework);
        if (scanner == null) {
            System.err.println("❌ No scanner available for framework: " + detectedFramework);
            return 1;
        }
        
        // Perform scan
        System.out.println("🔄 Scanning for API endpoints...");
        ScanResult result = scanner.scan(path);
        
        // Generate OpenAPI specification
        String openApiSpec = openApiGenerator.generate(result, format);
        
        // Save to file if output path specified
        if (outputPath != null) {
            Path outputFile = Paths.get(outputPath);
            openApiGenerator.saveToFile(openApiSpec, outputFile);
            System.out.println("✅ OpenAPI specification saved to: " + outputFile.toAbsolutePath());
        }
        
        // Show summary if requested
        if (showSummary) {
            System.out.println();
            reportGenerator.printSummary(result);
        }
        
        // Print spec to console if no output file specified
        if (outputPath == null && verbose) {
            System.out.println("\n📄 OpenAPI Specification:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println(openApiSpec);
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