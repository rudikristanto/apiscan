package com.apiscan.core.framework;

import java.nio.file.Path;

public interface FrameworkDetector {
    /**
     * Detects if this framework is used in the given project
     * @param projectPath the root path of the project to scan
     * @return true if the framework is detected, false otherwise
     */
    boolean detect(Path projectPath);
    
    /**
     * Gets the name of the framework this detector supports
     * @return framework name
     */
    String getFrameworkName();
    
    /**
     * Gets the priority of this detector (higher number = higher priority)
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }
}