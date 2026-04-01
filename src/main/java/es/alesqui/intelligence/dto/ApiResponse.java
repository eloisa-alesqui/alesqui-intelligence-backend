package es.alesqui.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Indicates if the operation was successful")
    private boolean success;

    @Schema(description = "Response message")
    private String message;

    @Schema(description = "Response data")
    private T data;

    @Schema(description = "Response timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "Error code (if applicable)")
    private String errorCode;
}
