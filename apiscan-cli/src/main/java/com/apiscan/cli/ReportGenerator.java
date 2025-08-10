package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {
    
    public void printSummary(ScanResult result) {
        System.out.println("📊 Scan Summary");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // Basic statistics
        System.out.println("📁 Project: " + result.getProjectPath());
        System.out.println("🔧 Framework: " + result.getFramework());
        System.out.println("📝 Files scanned: " + result.getFilesScanned());
        System.out.println("⏱️  Scan duration: " + result.getScanDurationMs() + " ms");
        System.out.println("🎯 Total endpoints found: " + result.getEndpoints().size());
        
        // Group endpoints by HTTP method
        Map<String, Long> methodCounts = result.getEndpoints().stream()
            .collect(Collectors.groupingBy(
                e -> e.getHttpMethod().toUpperCase(),
                Collectors.counting()
            ));
        
        if (!methodCounts.isEmpty()) {
            System.out.println("\n📌 Endpoints by HTTP Method:");
            methodCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String icon = getMethodIcon(entry.getKey());
                    System.out.printf("   %s %-8s: %d%n", icon, entry.getKey(), entry.getValue());
                });
        }
        
        // Group endpoints by controller
        Map<String, List<ApiEndpoint>> byController = result.getEndpoints().stream()
            .collect(Collectors.groupingBy(ApiEndpoint::getControllerClass));
        
        if (!byController.isEmpty()) {
            System.out.println("\n📦 Endpoints by Controller:");
            byController.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    System.out.printf("   • %-30s: %d endpoints%n", 
                        entry.getKey(), entry.getValue().size());
                });
        }
        
        // Show deprecated endpoints
        List<ApiEndpoint> deprecated = result.getEndpoints().stream()
            .filter(ApiEndpoint::isDeprecated)
            .collect(Collectors.toList());
        
        if (!deprecated.isEmpty()) {
            System.out.println("\n⚠️  Deprecated Endpoints: " + deprecated.size());
            deprecated.forEach(endpoint -> {
                System.out.printf("   • %s %s%n", 
                    endpoint.getHttpMethod(), endpoint.getPath());
            });
        }
        
        // Show errors if any
        if (!result.getErrors().isEmpty()) {
            System.out.println("\n❌ Errors encountered:");
            result.getErrors().forEach(error -> {
                System.out.println("   • " + error);
            });
        }
        
        // Show warnings if any
        if (!result.getWarnings().isEmpty()) {
            System.out.println("\n⚠️  Warnings:");
            result.getWarnings().forEach(warning -> {
                System.out.println("   • " + warning);
            });
        }
        
        // List all endpoints
        if (!result.getEndpoints().isEmpty()) {
            System.out.println("\n🔗 API Endpoints:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            // Sort endpoints by path and method
            List<ApiEndpoint> sortedEndpoints = result.getEndpoints().stream()
                .sorted(Comparator.comparing(ApiEndpoint::getPath)
                    .thenComparing(ApiEndpoint::getHttpMethod))
                .collect(Collectors.toList());
            
            for (ApiEndpoint endpoint : sortedEndpoints) {
                String methodIcon = getMethodIcon(endpoint.getHttpMethod());
                String deprecatedFlag = endpoint.isDeprecated() ? " [DEPRECATED]" : "";
                
                System.out.printf("%s %-7s %-40s %s%s%n",
                    methodIcon,
                    endpoint.getHttpMethod(),
                    truncate(endpoint.getPath(), 40),
                    endpoint.getControllerClass() + "." + endpoint.getMethodName(),
                    deprecatedFlag
                );
                
                // Show parameters if verbose
                if (!endpoint.getParameters().isEmpty()) {
                    String params = endpoint.getParameters().stream()
                        .map(p -> p.getName() + " (" + p.getIn() + ")")
                        .collect(Collectors.joining(", "));
                    System.out.println("         Parameters: " + params);
                }
            }
        }
        
        System.out.println("\n✅ Scan completed successfully!");
    }
    
    private String getMethodIcon(String method) {
        switch (method.toUpperCase()) {
            case "GET":
                return "🔍";
            case "POST":
                return "📤";
            case "PUT":
                return "📝";
            case "DELETE":
                return "🗑️";
            case "PATCH":
                return "🔧";
            default:
                return "📍";
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}