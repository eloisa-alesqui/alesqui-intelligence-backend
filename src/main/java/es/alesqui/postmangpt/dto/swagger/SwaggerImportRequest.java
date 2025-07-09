package es.alesqui.postmangpt.dto.swagger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for importing Swagger documents from content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for importing Swagger/OpenAPI content")
public class SwaggerImportRequest {

    @NotNull
    @NotBlank
    @Schema(description = "OpenAPI specification content (JSON or YAML)", 
            example = "{\n  \"openapi\": \"3.0.0\",\n  \"info\": {\n    \"title\": \"Sample API\",\n    \"version\": \"1.0.0\"\n  }\n}")
    private String content;

    @Schema(description = "Document name (optional, will use OpenAPI title if not provided)", 
            example = "My API Documentation")
    private String name;

    @Schema(description = "Document description", 
            example = "API documentation for my service")
    private String description;

    @NotNull
    @NotBlank
    @Schema(description = "Team owning the document", 
            example = "backend-team")
    private String team;

    @NotNull
    @NotBlank
    @Schema(description = "User creating the document", 
            example = "john.doe")
    private String createdBy;

    @Schema(description = "Content type hint (application/json or application/yaml)", 
            example = "application/json")
    private String contentType;
}
