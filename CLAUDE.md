# APISCAN - API Endpoint Scanner

## Project Overview
**apiscan** is a CLI application that extracts API endpoints from various implementation types and generates OpenAPI documentation.

## Core Requirements

### Goal
- Create OpenAPI file in JSON or YAML format
- Output summary report - informative to users

### Critical Design Constraints
- **NO COMPILATION/BUILD REQUIRED**: The input project to be analyzed should NOT be built or compiled. Many enterprise projects cannot be compiled due to dependencies, configurations, or access restrictions. The tool MUST analyze source code as-is by reading files directly.
- CLI application
- Enterprise grade quality

### Design Considerations
- Extensible to analyze other frameworks (Quarkus, Struts) and languages (.NET Core)
- Testable architecture
- Easy to maintain
- Best performance
- AST-based implementation for accurate extraction

### MVP Scope
- Java-based API implementations
- Spring MVC support
- Spring Boot support

### Test Projects
- `C:\Users\Rudi Kristanto\prj\spring-petclinic-rest`
- `C:\Users\Rudi Kristanto\prj\shopizer`

## Development Guidelines
1. Always analyze source code without compilation
2. Use AST parsing for accurate endpoint extraction
3. Focus on extensibility for future framework support
4. Maintain clear separation between framework-specific analyzers