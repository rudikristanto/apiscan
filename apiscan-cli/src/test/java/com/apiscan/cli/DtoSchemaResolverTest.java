package com.apiscan.cli;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DtoSchemaResolver to ensure proper DTO schema detection and generation.
 */
class DtoSchemaResolverTest {

    private DtoSchemaResolver resolver;
    
    @TempDir
    private Path tempProjectDir;

    @BeforeEach
    void setUp() {
        resolver = new DtoSchemaResolver(tempProjectDir.toString());
    }

    @Test
    void shouldCreatePlaceholderSchemaForMissingDto() {
        // Given
        String className = "MissingOwnerDto";
        
        // When
        Schema<?> schema = resolver.resolveSchema(className);
        
        // Then
        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getDescription()).contains("MissingOwnerDto");
        assertThat(schema.getDescription()).contains("Schema not available");
        
        // Should have placeholder property
        assertThat(schema.getProperties()).containsKey("_schemaPlaceholder");
        Schema<?> placeholderProp = (Schema<?>) schema.getProperties().get("_schemaPlaceholder");
        assertThat(placeholderProp.getType()).isEqualTo("string");
        assertThat(placeholderProp.getDescription()).contains("generated code");
    }

    @Test
    void shouldResolveSchemaFromGeneratedDtoFile() throws Exception {
        // Given - Create a mock generated DTO file
        Path generatedDir = tempProjectDir.resolve("target/generated-sources/openapi/src/main/java/com/example/dto");
        Files.createDirectories(generatedDir);
        
        Path ownerDtoFile = generatedDir.resolve("OwnerDto.java");
        Files.writeString(ownerDtoFile, createSampleOwnerDto());
        
        // When
        Schema<?> schema = resolver.resolveSchema("OwnerDto");
        
        // Then
        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getDescription()).contains("pet owner");
        
        // Should have properties parsed from the class
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKeys("firstName", "lastName", "address", "city", "telephone");
        
        // Check field types
        assertThat(properties.get("firstName").getType()).isEqualTo("string");
        assertThat(properties.get("telephone").getType()).isEqualTo("string");
        
        // Check required fields
        assertThat(schema.getRequired()).contains("firstName", "lastName", "address", "city", "telephone");
    }

    @Test
    void shouldResolveSchemaFromManualDtoFile() throws Exception {
        // Given - Create a manual DTO file
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        Path petDtoFile = srcDir.resolve("PetDto.java");
        Files.writeString(petDtoFile, createSamplePetDto());
        
        // When
        Schema<?> schema = resolver.resolveSchema("PetDto");
        
        // Then
        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getDescription()).contains("Pet");
        
        // Should have properties
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKeys("name", "petTypeId", "birthDate");
        
        // Check types
        assertThat(properties.get("name").getType()).isEqualTo("string");
        assertThat(properties.get("petTypeId").getType()).isEqualTo("integer");
        assertThat(properties.get("birthDate").getType()).isEqualTo("string");
        assertThat(properties.get("birthDate").getFormat()).isEqualTo("date-time");
        
        // Check required fields 
        assertThat(schema.getRequired()).contains("name");
    }

    @Test
    void shouldHandleCollectionFields() throws Exception {
        // Given - DTO with collection fields
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        Path dtoFile = srcDir.resolve("OwnerWithPetsDto.java");
        Files.writeString(dtoFile, createOwnerWithPetsDto());
        
        // When
        Schema<?> schema = resolver.resolveSchema("OwnerWithPetsDto");
        
        // Then
        assertThat(schema).isNotNull();
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKey("pets");
        
        Schema<?> petsProperty = properties.get("pets");
        assertThat(petsProperty.getType()).isEqualTo("array");
        assertThat(petsProperty.getItems()).isNotNull();
        
        // Items should either have a $ref (if PetDto is available) or be an object with description
        Schema<?> itemsSchema = petsProperty.getItems();
        boolean hasRef = itemsSchema.get$ref() != null;
        boolean hasObjectType = "object".equals(itemsSchema.getType());
        assertThat(hasRef || hasObjectType).isTrue();
    }

    @Test
    void shouldCreateProperReferencesForNestedDtos() throws Exception {
        // Given - DTOs with nested DTO references
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        // Create PetTypeDto first
        Path petTypeDtoFile = srcDir.resolve("PetTypeDto.java");
        Files.writeString(petTypeDtoFile, """
            package com.example.dto;
            
            public class PetTypeDto {
                private String name;
                private Integer id;
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Integer getId() { return id; }
                public void setId(Integer id) { this.id = id; }
            }
            """);
        
        // Create PetFieldsDto with nested PetTypeDto reference
        Path petFieldsDtoFile = srcDir.resolve("PetFieldsDto.java");
        Files.writeString(petFieldsDtoFile, """
            package com.example.dto;
            
            import java.time.LocalDateTime;
            
            public class PetFieldsDto {
                private String name;
                private PetTypeDto type;
                private LocalDateTime birthDate;
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public PetTypeDto getType() { return type; }
                public void setType(PetTypeDto type) { this.type = type; }
                public LocalDateTime getBirthDate() { return birthDate; }
                public void setBirthDate(LocalDateTime birthDate) { this.birthDate = birthDate; }
            }
            """);
        
        // When - resolve PetFieldsDto schema
        Schema<?> schema = resolver.resolveSchema("PetFieldsDto");
        
        // Then - verify proper DTO reference
        assertThat(schema).isNotNull();
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKey("type");
        
        Schema<?> typeProperty = properties.get("type");
        assertThat(typeProperty.get$ref()).isEqualTo("#/components/schemas/PetTypeDto");
        assertThat(typeProperty.getType()).isNull(); // $ref should not have type property
        
        // Verify other properties are handled correctly
        assertThat(properties.get("name").getType()).isEqualTo("string");
        assertThat(properties.get("birthDate").getType()).isEqualTo("string");
        assertThat(properties.get("birthDate").getFormat()).isEqualTo("date-time");
    }

    @Test
    void shouldCacheResolvedSchemas() {
        // Given
        String className = "CacheTestDto";
        
        // When - Resolve same DTO twice
        Schema<?> schema1 = resolver.resolveSchema(className);
        Schema<?> schema2 = resolver.resolveSchema(className);
        
        // Then - Should return the same cached instance
        assertThat(schema1).isSameAs(schema2);
        assertThat(resolver.getCachedSchemaCount()).isEqualTo(1);
    }

    @Test
    void shouldClearCache() {
        // Given
        resolver.resolveSchema("TestDto1");
        resolver.resolveSchema("TestDto2");
        assertThat(resolver.getCachedSchemaCount()).isEqualTo(2);
        
        // When
        resolver.clearCache();
        
        // Then
        assertThat(resolver.getCachedSchemaCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullAndEmptyClassNames() {
        // When/Then
        Schema<?> nullSchema = resolver.resolveSchema(null);
        assertThat(nullSchema.getType()).isEqualTo("object");
        assertThat(nullSchema.getDescription()).contains("Unknown");
        
        Schema<?> emptySchema = resolver.resolveSchema("");
        assertThat(emptySchema.getType()).isEqualTo("object");
        assertThat(emptySchema.getDescription()).contains("Unknown");
        
        Schema<?> blankSchema = resolver.resolveSchema("   ");
        assertThat(blankSchema.getType()).isEqualTo("object");
        assertThat(blankSchema.getDescription()).contains("Unknown");
    }

    @Test
    void shouldHandleValidationAnnotations() throws Exception {
        // Given - DTO with validation annotations
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        Path dtoFile = srcDir.resolve("ValidatedDto.java");
        Files.writeString(dtoFile, createValidatedDto());
        
        // When
        Schema<?> schema = resolver.resolveSchema("ValidatedDto");
        
        // Then
        assertThat(schema).isNotNull();
        
        // Fields with @NotNull should be required
        assertThat(schema.getRequired()).contains("requiredField");
        assertThat(schema.getRequired()).doesNotContain("optionalField");
    }

    @Test
    void shouldPreferGeneratedOverManualDtos() throws Exception {
        // Given - Both generated and manual versions exist
        // Generated version
        Path generatedDir = tempProjectDir.resolve("target/generated-sources/openapi/src/main/java/com/example/dto");
        Files.createDirectories(generatedDir);
        Path generatedFile = generatedDir.resolve("ConflictDto.java");
        Files.writeString(generatedFile, createSampleDto("ConflictDto", "Generated DTO description"));
        
        // Manual version  
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        Path manualFile = srcDir.resolve("ConflictDto.java");
        Files.writeString(manualFile, createSampleDto("ConflictDto", "Manual DTO description"));
        
        // When
        Schema<?> schema = resolver.resolveSchema("ConflictDto");
        
        // Then - Should prefer generated version (appears first in search paths)
        assertThat(schema.getDescription()).contains("Generated DTO description");
    }

    @Test
    void shouldHandleBrokenSchemaReferences() throws Exception {
        // Given - A DTO that references another DTO that doesn't exist
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        Path userDtoFile = srcDir.resolve("UserDto.java");
        Files.writeString(userDtoFile, createUserDtoWithRoles());
        
        // When - resolve UserDto schema (references non-existent RoleDto)
        Schema<?> schema = resolver.resolveSchema("UserDto");
        
        // Then - should handle gracefully
        assertThat(schema).isNotNull();
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKey("roles");
        
        Schema<?> rolesProperty = properties.get("roles");
        assertThat(rolesProperty.getType()).isEqualTo("array");
        
        // The items should either be a reference or a fallback object
        Schema<?> itemsSchema = rolesProperty.getItems();
        assertThat(itemsSchema).isNotNull();
        
        // Should not generate broken references - either valid $ref or object fallback
        if (itemsSchema.get$ref() != null) {
            // If it's a reference, should also resolve the referenced schema
            Schema<?> roleSchema = resolver.resolveSchema("RoleDto");
            assertThat(roleSchema).isNotNull();
        } else {
            // If not a reference, should be a proper object
            assertThat(itemsSchema.getType()).isEqualTo("object");
            assertThat(itemsSchema.getDescription()).isNotNull();
        }
    }

    private String createSampleOwnerDto() {
        return """
            package com.example.dto;
            
            import jakarta.validation.constraints.NotNull;
            import jakarta.validation.constraints.Pattern;
            import jakarta.validation.constraints.Size;
            import io.swagger.v3.oas.annotations.media.Schema;
            
            @Schema(name = "Owner", description = "A pet owner.")
            public class OwnerDto {
                
                @NotNull
                @Size(min = 1, max = 30)
                @Pattern(regexp = "^[a-zA-Z]*$")
                private String firstName;
                
                @NotNull
                @Size(min = 1, max = 30) 
                @Pattern(regexp = "^[a-zA-Z]*$")
                private String lastName;
                
                @NotNull
                @Size(min = 1, max = 255)
                private String address;
                
                @NotNull
                @Size(min = 1, max = 80)
                private String city;
                
                @NotNull
                @Size(min = 1, max = 20)
                @Pattern(regexp = "^[0-9]*$")
                private String telephone;
                
                private Integer id;
                
                // Getters and setters
                public String getFirstName() { return firstName; }
                public void setFirstName(String firstName) { this.firstName = firstName; }
                
                public String getLastName() { return lastName; }
                public void setLastName(String lastName) { this.lastName = lastName; }
                
                public String getAddress() { return address; }
                public void setAddress(String address) { this.address = address; }
                
                public String getCity() { return city; }
                public void setCity(String city) { this.city = city; }
                
                public String getTelephone() { return telephone; }
                public void setTelephone(String telephone) { this.telephone = telephone; }
                
                public Integer getId() { return id; }
                public void setId(Integer id) { this.id = id; }
            }
            """;
    }

    private String createSamplePetDto() {
        return """
            package com.example.dto;
            
            import jakarta.validation.constraints.NotNull;
            import java.time.LocalDate;
            
            /**
             * Pet DTO class.
             */
            public class PetDto {
                
                @NotNull
                private String name;
                
                private Integer petTypeId;
                
                private LocalDate birthDate;
                
                // Getters and setters
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public Integer getPetTypeId() { return petTypeId; }
                public void setPetTypeId(Integer petTypeId) { this.petTypeId = petTypeId; }
                
                public LocalDate getBirthDate() { return birthDate; }
                public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
            }
            """;
    }

    private String createOwnerWithPetsDto() {
        return """
            package com.example.dto;
            
            import java.util.List;
            
            public class OwnerWithPetsDto {
                private String name;
                private List<PetDto> pets;
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public List<PetDto> getPets() { return pets; }
                public void setPets(List<PetDto> pets) { this.pets = pets; }
            }
            """;
    }

    private String createValidatedDto() {
        return """
            package com.example.dto;
            
            import jakarta.validation.constraints.NotNull;
            
            public class ValidatedDto {
                @NotNull
                private String requiredField;
                
                private String optionalField;
                
                public String getRequiredField() { return requiredField; }
                public void setRequiredField(String requiredField) { this.requiredField = requiredField; }
                
                public String getOptionalField() { return optionalField; }
                public void setOptionalField(String optionalField) { this.optionalField = optionalField; }
            }
            """;
    }

    private String createSampleDto(String className, String description) {
        return String.format("""
            package com.example.dto;
            
            /**
             * %s
             */
            public class %s {
                private String testField;
                
                public String getTestField() { return testField; }
                public void setTestField(String testField) { this.testField = testField; }
            }
            """, description, className);
    }

    private String createUserDtoWithRoles() {
        return """
            package com.example.dto;
            
            import java.util.List;
            
            public class UserDto {
                private String username;
                private List<RoleDto> roles;
                
                public String getUsername() { return username; }
                public void setUsername(String username) { this.username = username; }
                
                public List<RoleDto> getRoles() { return roles; }
                public void setRoles(List<RoleDto> roles) { this.roles = roles; }
            }
            """;
    }

    @Test
    void shouldResolveSchemaFromLombokDto() throws Exception {
        // Given - Create a Lombok DTO file (like AccountDto)
        Path srcDir = tempProjectDir.resolve("src/main/java/com/example/dto");
        Files.createDirectories(srcDir);
        
        Path lombokDtoFile = srcDir.resolve("AccountDto.java");
        Files.writeString(lombokDtoFile, createLombokAccountDto());
        
        // When
        Schema<?> schema = resolver.resolveSchema("AccountDto");
        
        // Then
        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getDescription()).contains("AccountDto");
        
        // Should have properties even without explicit getters (due to @Data)
        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties).containsKeys("accountId", "accountNumber", "accountType", "accountStatus", "availableBalance", "userId");
        
        // Check field types
        assertThat(properties.get("accountId").getType()).isEqualTo("integer");
        assertThat(properties.get("accountNumber").getType()).isEqualTo("string");
        assertThat(properties.get("accountType").getType()).isEqualTo("string");
        assertThat(properties.get("accountStatus").getType()).isEqualTo("string");
        assertThat(properties.get("availableBalance").getType()).isEqualTo("number");
        assertThat(properties.get("userId").getType()).isEqualTo("integer");
    }

    private String createLombokAccountDto() {
        return """
            package com.example.dto;
            
            import lombok.AllArgsConstructor;
            import lombok.Builder;
            import lombok.Data;
            import lombok.NoArgsConstructor;
            import java.math.BigDecimal;
            
            @Data
            @AllArgsConstructor
            @NoArgsConstructor
            @Builder
            public class AccountDto {
                
                private Long accountId;
                private String accountNumber;
                private String accountType;
                private String accountStatus;
                private BigDecimal availableBalance;
                private Long userId;
            }
            """;
    }
}