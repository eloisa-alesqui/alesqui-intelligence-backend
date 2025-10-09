package es.alesqui.intelligence.dto.chat.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * DTO for providing structured, machine-readable error details to the AI model.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredApiError {
    private String errorType;
    private String message;
    private Map<String, Object> details;
}