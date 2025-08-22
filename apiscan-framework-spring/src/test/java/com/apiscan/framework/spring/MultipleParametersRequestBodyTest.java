package com.apiscan.framework.spring;

import com.apiscan.core.model.ApiEndpoint;
import com.apiscan.core.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for correct request body extraction when methods have multiple parameters,
 * including framework types like Principal that should be ignored.
 * This addresses the piggymetrics issue where Principal was incorrectly selected as request body.
 */
public class MultipleParametersRequestBodyTest {

    @TempDir
    Path tempDir;

    @Test
    public void testRequestBodyWithPrincipalParameter() throws IOException {
        // Create a controller with methods that have Principal and @RequestBody parameters
        String controllerCode = """
            package com.test.api;
            
            import org.springframework.web.bind.annotation.*;
            import javax.validation.Valid;
            import java.security.Principal;
            
            @RestController
            @RequestMapping("/api")
            public class NotificationController {
                
                // Method with Principal first, then @RequestBody - common pattern in secured endpoints
                @PutMapping("/notifications/current")
                public Object updateNotification(Principal principal, @Valid @RequestBody NotificationDto notification) {
                    return "updated";
                }
                
                // Method with @RequestBody first, then Principal - should also work
                @PostMapping("/notifications")
                public Object createNotification(@RequestBody NotificationDto notification, Principal principal) {
                    return "created";
                }
                
                // Method with Principal and domain object without @RequestBody annotation
                @PutMapping("/recipients/current")
                public Object updateRecipient(Principal principal, RecipientEntity recipient) {
                    return "updated";
                }
                
                // Method with HttpServletRequest and @RequestBody
                @PostMapping("/secure")
                public Object secureEndpoint(HttpServletRequest request, @RequestBody SecureDto data) {
                    return "processed";
                }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test/api");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("NotificationController.java"), controllerCode);

        // Scan the project
        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);

        // Should find 4 endpoints
        assertEquals(4, result.getEndpoints().size());

        // Test PUT /api/notifications/current - should have NotificationDto as request body, not Principal
        ApiEndpoint putNotification = findEndpoint(result.getEndpoints(), "PUT", "/api/notifications/current");
        assertNotNull(putNotification, "Should find PUT /api/notifications/current endpoint");
        assertNotNull(putNotification.getRequestBody(), "Should have request body");
        assertEquals("NotificationDto", 
            putNotification.getRequestBody().getContent().get("application/json").getSchema(),
            "Request body should be NotificationDto, not Principal");

        // Test POST /api/notifications - should also have NotificationDto as request body
        ApiEndpoint postNotification = findEndpoint(result.getEndpoints(), "POST", "/api/notifications");
        assertNotNull(postNotification, "Should find POST /api/notifications endpoint");
        assertNotNull(postNotification.getRequestBody(), "Should have request body");
        assertEquals("NotificationDto", 
            postNotification.getRequestBody().getContent().get("application/json").getSchema(),
            "Request body should be NotificationDto even when it comes before Principal");

        // Test PUT /api/recipients/current - should infer RecipientEntity as request body
        ApiEndpoint putRecipient = findEndpoint(result.getEndpoints(), "PUT", "/api/recipients/current");
        assertNotNull(putRecipient, "Should find PUT /api/recipients/current endpoint");
        assertNotNull(putRecipient.getRequestBody(), "Should have request body");
        assertEquals("RecipientEntity", 
            putRecipient.getRequestBody().getContent().get("application/json").getSchema(),
            "Should infer RecipientEntity as request body since it's a domain object");

        // Test POST /api/secure - should have SecureDto as request body, not HttpServletRequest
        ApiEndpoint postSecure = findEndpoint(result.getEndpoints(), "POST", "/api/secure");
        assertNotNull(postSecure, "Should find POST /api/secure endpoint");
        assertNotNull(postSecure.getRequestBody(), "Should have request body");
        assertEquals("SecureDto", 
            postSecure.getRequestBody().getContent().get("application/json").getSchema(),
            "Request body should be SecureDto, not HttpServletRequest");
    }

    @Test
    public void testFrameworkTypesAreIgnored() throws IOException {
        // Test that various framework types are not selected as request bodies
        String controllerCode = """
            package com.test.api;
            
            import org.springframework.web.bind.annotation.*;
            import java.security.Principal;
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.http.HttpSession;
            import java.util.Locale;
            import org.springframework.security.core.Authentication;
            
            @RestController
            @RequestMapping("/test")
            public class FrameworkTypeController {
                
                @PostMapping("/principal")
                public void withPrincipal(Principal principal, UserDto user) {
                }
                
                @PostMapping("/servlet")
                public void withServlet(HttpServletRequest request, HttpServletResponse response, DataDto data) {
                }
                
                @PostMapping("/session")
                public void withSession(HttpSession session, ConfigDto config) {
                }
                
                @PostMapping("/locale")
                public void withLocale(Locale locale, SettingsDto settings) {
                }
                
                @PostMapping("/auth")
                public void withAuth(Authentication auth, ProfileDto profile) {
                }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test/api");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("FrameworkTypeController.java"), controllerCode);

        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);

        assertEquals(5, result.getEndpoints().size());

        // All endpoints should have the DTO as request body, not the framework types
        ApiEndpoint principalEndpoint = findEndpoint(result.getEndpoints(), "POST", "/test/principal");
        assertNotNull(principalEndpoint.getRequestBody());
        assertEquals("UserDto", principalEndpoint.getRequestBody().getContent().get("application/json").getSchema());

        ApiEndpoint servletEndpoint = findEndpoint(result.getEndpoints(), "POST", "/test/servlet");
        assertNotNull(servletEndpoint.getRequestBody());
        assertEquals("DataDto", servletEndpoint.getRequestBody().getContent().get("application/json").getSchema());

        ApiEndpoint sessionEndpoint = findEndpoint(result.getEndpoints(), "POST", "/test/session");
        assertNotNull(sessionEndpoint.getRequestBody());
        assertEquals("ConfigDto", sessionEndpoint.getRequestBody().getContent().get("application/json").getSchema());

        ApiEndpoint localeEndpoint = findEndpoint(result.getEndpoints(), "POST", "/test/locale");
        assertNotNull(localeEndpoint.getRequestBody());
        assertEquals("SettingsDto", localeEndpoint.getRequestBody().getContent().get("application/json").getSchema());

        ApiEndpoint authEndpoint = findEndpoint(result.getEndpoints(), "POST", "/test/auth");
        assertNotNull(authEndpoint.getRequestBody());
        assertEquals("ProfileDto", authEndpoint.getRequestBody().getContent().get("application/json").getSchema());
    }

    @Test
    public void testExplicitRequestBodyTakesPrecedence() throws IOException {
        // Test that explicit @RequestBody annotation always takes precedence
        String controllerCode = """
            package com.test.api;
            
            import org.springframework.web.bind.annotation.*;
            import java.security.Principal;
            
            @RestController
            public class PrecedenceController {
                
                // Multiple DTOs but only one with @RequestBody
                @PostMapping("/explicit")
                public void multipleDto(FirstDto first, @RequestBody SecondDto second, ThirdDto third) {
                }
                
                // Principal with explicit @RequestBody on a simple type (edge case)
                @PostMapping("/string-body")
                public void stringBody(Principal principal, @RequestBody String content) {
                }
            }
            """;

        Path srcDir = tempDir.resolve("src/main/java/com/test/api");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("PrecedenceController.java"), controllerCode);

        SpringFrameworkScanner scanner = new SpringFrameworkScanner();
        ScanResult result = scanner.scan(tempDir);

        assertEquals(2, result.getEndpoints().size());

        // Should select SecondDto because it has @RequestBody
        ApiEndpoint explicitEndpoint = findEndpoint(result.getEndpoints(), "POST", "/explicit");
        assertNotNull(explicitEndpoint.getRequestBody());
        assertEquals("SecondDto", explicitEndpoint.getRequestBody().getContent().get("application/json").getSchema());

        // Should select String even though it's a simple type because of @RequestBody
        ApiEndpoint stringEndpoint = findEndpoint(result.getEndpoints(), "POST", "/string-body");
        assertNotNull(stringEndpoint.getRequestBody());
        assertEquals("String", stringEndpoint.getRequestBody().getContent().get("application/json").getSchema());
    }

    private ApiEndpoint findEndpoint(List<ApiEndpoint> endpoints, String method, String path) {
        return endpoints.stream()
            .filter(e -> method.equals(e.getHttpMethod()) && path.equals(e.getPath()))
            .findFirst()
            .orElse(null);
    }
}