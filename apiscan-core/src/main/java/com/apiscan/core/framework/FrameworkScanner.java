package com.apiscan.core.framework;

import com.apiscan.core.model.ScanResult;
import java.nio.file.Path;

public interface FrameworkScanner {
    /**
     * Scans the project for API endpoints
     * @param projectPath the root path of the project to scan
     * @return scan result containing found endpoints
     */
    ScanResult scan(Path projectPath);
    
    /**
     * Gets the name of the framework this scanner supports
     * @return framework name
     */
    String getFrameworkName();
    
    /**
     * Checks if this scanner supports the given framework
     * @param frameworkName name of the framework
     * @return true if supported, false otherwise
     */
    default boolean supports(String frameworkName) {
        return getFrameworkName().equalsIgnoreCase(frameworkName);
    }
}