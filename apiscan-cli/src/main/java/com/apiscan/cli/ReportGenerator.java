package com.apiscan.cli;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

public class ReportGenerator {
    
    public void printSummary(ScanResult result) {
        printSummary(result, result.getScanDurationMs());
    }
    
    public void printSummary(ScanResult result, long scanTimeMs) {
        System.out.println();
        System.out.println("=========================================================");
        System.out.println("|                    SCAN SUMMARY                      |");
        System.out.println("=========================================================");
        
        // Basic statistics
        String projectName = result.getProjectPath().toString();
        int lastSlash = Math.max(projectName.lastIndexOf('\\'), projectName.lastIndexOf('/'));
        if (lastSlash >= 0 && lastSlash < projectName.length() - 1) {
            projectName = projectName.substring(lastSlash + 1);
        }
        System.out.printf("Project:          %s%n", projectName);
        System.out.printf("Framework:        %s%n", result.getFramework());
        System.out.printf("Files scanned:    %,d%n", result.getFilesScanned());
        System.out.printf("Duration:         %,d ms%n", scanTimeMs);
        System.out.printf("Total endpoints:  %d%n", result.getEndpoints().size());
        
        // Group endpoints by HTTP method
        Map<String, Long> methodCounts = result.getEndpoints().stream()
            .collect(Collectors.groupingBy(
                e -> e.getHttpMethod().toUpperCase(),
                Collectors.counting()
            ));
        
        if (!methodCounts.isEmpty()) {
            System.out.println();
            System.out.println("HTTP Methods:");
            System.out.println("-----------------------------------------");
            methodCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String icon = getMethodIcon(entry.getKey());
                    System.out.printf("  %-7s %-8s %2d endpoints%n", 
                        icon, 
                        entry.getKey(), 
                        entry.getValue());
                });
        }
        
        // Group endpoints by controller
        Map<String, List<ApiEndpoint>> byController = result.getEndpoints().stream()
            .collect(Collectors.groupingBy(ApiEndpoint::getControllerClass));
        
        if (!byController.isEmpty()) {
            System.out.println();
            System.out.println("Controllers:");
            System.out.println("-----------------------------------------");
            byController.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .forEach(entry -> {
                    String controllerName = entry.getKey();
                    System.out.printf("  [%2d] %-35s%n", 
                        entry.getValue().size(),
                        truncate(controllerName, 35));
                });
        }
        
        // Show deprecated endpoints
        List<ApiEndpoint> deprecated = result.getEndpoints().stream()
            .filter(ApiEndpoint::isDeprecated)
            .collect(Collectors.toList());
        
        if (!deprecated.isEmpty()) {
            System.out.println();
            System.out.println("Deprecated Endpoints: " + deprecated.size());
            System.out.println("-----------------------------------------");
            deprecated.forEach(endpoint -> {
                System.out.printf("  [DEPRECATED] %s %s%n", 
                    endpoint.getHttpMethod(), endpoint.getPath());
            });
        }
        
        // Show errors if any
        if (!result.getErrors().isEmpty()) {
            System.out.println();
            System.out.println("Errors encountered:");
            System.out.println("-----------------------------------------");
            result.getErrors().forEach(error -> {
                System.out.println("  [ERROR] " + error);
            });
        }
        
        // Show warnings if any
        if (!result.getWarnings().isEmpty()) {
            System.out.println();
            System.out.println("Warnings:");
            System.out.println("-----------------------------------------");
            result.getWarnings().forEach(warning -> {
                System.out.println("  [WARNING] " + warning);
            });
        }
        
        // List all endpoints
        if (!result.getEndpoints().isEmpty()) {
            System.out.println();
            System.out.println("=========================================================");
            System.out.println("|                    API ENDPOINTS                     |");
            System.out.println("=========================================================");
            
            // Group and display by controller
            byController.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(controllerEntry -> {
                    System.out.println();
                    System.out.println("Controller: " + controllerEntry.getKey());
                    System.out.println("-".repeat(70));
                    
                    controllerEntry.getValue().stream()
                        .sorted(Comparator.comparing(ApiEndpoint::getPath)
                            .thenComparing(ApiEndpoint::getHttpMethod))
                        .forEach(endpoint -> {
                            String methodIcon = getMethodIcon(endpoint.getHttpMethod());
                            String deprecatedFlag = endpoint.isDeprecated() ? " [DEPRECATED]" : "";
                            
                            // Format the main endpoint line with proper alignment
                            // Use fixed 8-char width for method icon to ensure URL alignment
                            String methodDisplay = String.format("%-8s", methodIcon);
                            String path = String.format("%-40s", truncate(endpoint.getPath(), 40));
                            String methodName = endpoint.getMethodName();
                            
                            System.out.printf("  %s %s %s%s%n",
                                methodDisplay,
                                path,
                                methodName,
                                deprecatedFlag
                            );
                            
                            // Show parameters with proper indentation
                            if (!endpoint.getParameters().isEmpty()) {
                                String params = endpoint.getParameters().stream()
                                    .map(p -> p.getName())
                                    .collect(Collectors.joining(", "));
                                if (params.length() > 45) {
                                    params = params.substring(0, 42) + "...";
                                }
                                // Use 11-space indentation to align under the URL
                                System.out.printf("           Parameters: %s%n", params);
                            }
                            
                            // Show request body if present
                            if (endpoint.getRequestBody() != null && !endpoint.getRequestBody().getContent().isEmpty()) {
                                String bodyType = endpoint.getRequestBody().getContent().values().stream()
                                    .findFirst()
                                    .map(media -> media.getSchema())
                                    .orElse("Unknown");
                                // Use 11-space indentation to align under the URL
                                System.out.printf("           Request Body: %s%n", bodyType);
                            }
                        });
                });
        }
        
        System.out.println();
        System.out.println("=========================================================");
        System.out.println("|             SCAN COMPLETED SUCCESSFULLY!             |");
        System.out.println("=========================================================");
    }
    
    private String getMethodIcon(String method) {
        switch (method.toUpperCase()) {
            case "GET":
                return "[GET]";
            case "POST":
                return "[POST]";
            case "PUT":
                return "[PUT]";
            case "DELETE":
                return "[DEL]";
            case "PATCH":
                return "[PATCH]";
            default:
                return "[?]";
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