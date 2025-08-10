package com.apiscan.core.model;

import java.util.ArrayList;
import java.util.List;

public class ScanResult {
    private String projectPath;
    private String framework;
    private List<ApiEndpoint> endpoints = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private long scanDurationMs;
    private int filesScanned;

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public List<ApiEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<ApiEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public long getScanDurationMs() {
        return scanDurationMs;
    }

    public void setScanDurationMs(long scanDurationMs) {
        this.scanDurationMs = scanDurationMs;
    }

    public int getFilesScanned() {
        return filesScanned;
    }

    public void setFilesScanned(int filesScanned) {
        this.filesScanned = filesScanned;
    }

    public void addEndpoint(ApiEndpoint endpoint) {
        this.endpoints.add(endpoint);
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}