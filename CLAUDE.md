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

## Key Features

### Interface-Based Endpoint Detection
The Spring framework scanner supports multiple approaches for API endpoint detection:

1. **Direct Annotation Scanning**: Extracts endpoints from controller classes with Spring mapping annotations (`@GetMapping`, `@PostMapping`, etc.)

2. **Interface Implementation Analysis**: When controllers implement API interfaces (common in generated code or contract-first development), the scanner:
   - Collects all interfaces with Spring mapping annotations in a first pass
   - Matches controller classes that implement these interfaces
   - Extracts endpoint definitions from the interface methods

3. **Method Inference**: For cases where interface definitions are not available (external dependencies, generated code), the scanner can infer API endpoints from controller methods marked with `@Override`:
   - Analyzes method names to determine HTTP methods (`listOwners` → GET, `addOwner` → POST, `updateOwner` → PUT, `deleteOwner` → DELETE)
   - Constructs RESTful paths based on method names and parameters (`getOwner(Integer ownerId)` → `/owners/{ownerId}`)
   - Supports nested resource patterns (`addPetToOwner` → `/owners/{ownerId}/pets`)

### Test Results
- **spring-petclinic-rest**: Successfully detected 35 endpoints across 8 controllers
- **Before improvement**: 1 endpoint detected
- **After improvement**: 35 endpoints detected (35x improvement)

### Supported Patterns
- Standard REST controllers with direct annotations
- Interface-based API definitions (OpenAPI generated, contract-first)
- Mixed patterns within the same project
- Nested resource relationships (owners/pets/visits)
- All standard HTTP methods (GET, POST, PUT, DELETE, PATCH)

## Test Coverage

### Comprehensive Test Suite
The APISCAN tool includes extensive test coverage for all major enterprise Spring Java application scenarios:

#### Core Component Tests
- **JavaSourceParserTest**: AST parsing functionality, file handling, error scenarios
- **ApiEndpointTest**: Data model validation, parameter handling, request/response structures

#### Enterprise API Pattern Tests
1. **Direct Annotation-Based APIs**
   - Controllers with Spring mapping annotations (`@GetMapping`, `@PostMapping`, etc.)
   - Standard RESTful CRUD operations
   - Path parameter and request body handling
   - Example: `@RestController` with `@RequestMapping("/api/users")`

2. **Interface-Based APIs with Available Definitions**
   - Controllers implementing interfaces that contain Spring annotations
   - Contract-first development patterns
   - OpenAPI code generation scenarios
   - Example: `ProductController implements ProductsApi` where `ProductsApi` has annotations

3. **Interface-Based APIs with Missing Definitions (@Override Inference)**
   - Controllers implementing external interfaces (JAR dependencies, generated code)
   - Intelligent endpoint inference from method names and parameters
   - Method pattern recognition (`listOrders` → GET, `addOrder` → POST, `deleteOrder` → DELETE)
   - Path construction from method signatures (`getOrder(Integer orderId)` → `/orders/{orderId}`)
   - Example: Real-world scenario like spring-petclinic-rest project

4. **Mixed Implementation Scenarios**
   - Projects combining both direct annotation and interface-based controllers
   - Multiple architectural patterns within the same codebase
   - Different teams using different approaches

5. **Complex Nested Resource Hierarchies**
   - Multi-level REST resource relationships
   - Enterprise-grade resource structures
   - Examples: 
     - `companies/{id}/departments/{id}/employees/{id}/projects`
     - `owners/{id}/pets/{id}/visits`
   - Intelligent nested path inference from method names

6. **HTTP Method and Annotation Variations**
   - `@RequestMapping` with method parameters
   - Specialized annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`)
   - Parameter annotations (`@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader`)
   - Security annotations (`@PreAuthorize`) - handled gracefully

7. **CLI Integration Testing**
   - End-to-end command-line interface testing
   - Output format validation (JSON/YAML)
   - Error handling and edge cases
   - Real project scanning verification

#### Test Scenarios Covering Enterprise Requirements

**Scenario 1: Legacy Enterprise Application**
- Mixed direct annotations and interface implementations
- Complex business domain models
- Multi-module Maven projects
- Security-enabled endpoints

**Scenario 2: Microservices Architecture**
- Contract-first API development
- Generated client/server stubs
- Interface definitions in separate modules
- Cross-service communication patterns

**Scenario 3: Code Generation Workflows**
- OpenAPI specification → Java code generation
- Missing interface source code (JAR dependencies)
- Method signature-based endpoint inference
- Automated documentation generation

**Scenario 4: Migration Projects**
- Gradual migration from older frameworks
- Mixed annotation styles within controllers
- Backward compatibility requirements
- Documentation catch-up scenarios

**Scenario 5: Enterprise Integration**
- Complex nested resource hierarchies
- Business entity relationships
- Multi-level authorization patterns
- Comprehensive API documentation needs

### Test Execution and Quality Assurance
- **Maven Integration**: Tests run as part of build process (`mvn test`)
- **Java 17 Compatibility**: Modern language features with text blocks
- **Flexible Assertions**: Robust test expectations that accommodate inference variations
- **Performance Testing**: Large project scanning (spring-petclinic-rest: 35 endpoints)
- **Error Handling**: Malformed code, missing dependencies, parsing failures
- **Output Validation**: Generated OpenAPI specifications, CLI reports

### Continuous Quality Metrics
- **Code Coverage**: Core parsing, framework detection, endpoint extraction
- **Real-world Validation**: Tested against production Spring projects
- **Regression Testing**: Ensures existing functionality remains intact
- **Edge Case Handling**: Unusual method names, complex generics, annotation variations

## Enhanced User Experience

### Professional CLI Output Formatting
The APISCAN tool now features enterprise-grade console output with improved readability and professional presentation:

#### Key Improvements
1. **Professional Header Design**: Clean ASCII formatting and clear branding (Windows-compatible)
   ```
   =========================================================
   |                   APISCAN v1.0.0                     |
   |            Enterprise API Endpoint Scanner           |
   =========================================================
   ```

2. **Clean Progress Indicators**: Clear status messages without verbose debug output
   - Framework detection confirmation
   - Scan progress with timing information
   - Endpoint discovery count
   - OpenAPI generation status

3. **Structured Summary Reports**: Professionally formatted scan results
   - **Project Information**: Clean project name extraction and framework details
   - **Performance Metrics**: File scan counts and duration with proper number formatting
   - **HTTP Method Breakdown**: Tabulated endpoint counts by method with icons
   - **Controller Organization**: Sorted by endpoint count, easy to identify high-traffic controllers
   - **Detailed Endpoint Listing**: Organized by controller with hierarchical display

4. **Enhanced Data Visualization**:
   - **HTTP Method Indicators**: [GET], [POST], [PUT], [DEL], [PATCH]
   - **ASCII-Safe Formatting**: Cross-platform compatible console output
   - **Parameter Display**: Compact parameter information without clutter
   - **Deprecation Indicators**: Clear [DEPRECATED] marking of deprecated endpoints

5. **Reduced Debug Noise**: 
   - **Production Logging**: Debug information sent to log files, not console
   - **Clean Console Output**: Only essential information displayed to users
   - **Verbose Mode Available**: Full details accessible with `-v` flag when needed

#### Example Professional Output
```
[INFO] Framework detected: Spring
[INFO] Scanning for API endpoints...
[SUCCESS] Scan completed in 1,266ms
[RESULT] Found 35 API endpoints

=========================================================
|                    SCAN SUMMARY                      |
=========================================================
Project:          spring-petclinic-rest
Framework:        Spring  
Files scanned:    83
Duration:         1,266 ms
Total endpoints:  35

HTTP Methods:
-----------------------------------------
  [DEL] DELETE   6 endpoints
  [GET] GET      14 endpoints
  [POST] POST     8 endpoints
  [PUT] PUT      7 endpoints

Controllers:
-----------------------------------------
  [ 9] OwnerRestController
  [ 5] SpecialtyRestController
  [ 5] VetRestController
  [ 5] PetTypeRestController
  [ 5] VisitRestController
  [ 4] PetRestController
  [ 1] UserRestController
  [ 1] RootRestController

=========================================================
|             SCAN COMPLETED SUCCESSFULLY!             |
=========================================================
```

### Output Format Testing
Comprehensive test coverage for the enhanced output formatting:
- **ReportGeneratorTest**: Complete test suite validating all formatting improvements
- **ASCII-Safe Presentation**: Verification of cross-platform compatible formatting
- **Content Organization**: Testing of tabular layouts and hierarchical displays  
- **Edge Case Handling**: Long controller names, empty results, error scenarios
- **Console Output Capture**: Systematic validation of all output sections

### Cross-Platform Compatibility
The enhanced output formatting addresses common Windows console encoding issues:
- **ASCII-Safe Characters**: Replaced Unicode box-drawing and emoji with standard ASCII
- **Windows Console Support**: Fully compatible with Windows Command Prompt and PowerShell
- **Universal Display**: Consistent appearance across Windows, macOS, and Linux terminals
- **Character Encoding Independent**: No dependency on UTF-8 console support
- **Professional Appearance**: Maintains enterprise-grade visual quality with standard characters

### User Experience Benefits
1. **Professional Appearance**: Enterprise-grade console output suitable for business presentations
2. **Information Density**: Maximum useful information in minimal screen space
3. **Quick Scanning**: Easy to identify key metrics and problem areas at a glance
4. **Consistent Formatting**: Standardized presentation across all scan results
5. **Reduced Cognitive Load**: Clean, organized information hierarchy
6. **Tool Credibility**: Professional appearance enhances user trust and adoption