package es.alesqui.intelligence.dto.chat.reasoning;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Data Transfer Object (DTO) that encapsulates all information
 * about a single tool execution.
 *
 * It includes the tool's name, the arguments (request) sent to it,
 * and the data (response) it returned. It is designed to be inspected
 * in the frontend modal.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallData {

    /**
     * The name of the function or tool that was called 
     * (e.g., "call_api", "create_chart").
     */
    private String toolName;

    /**
     * The JSON string representation of the arguments passed to the tool.
     * This is the 'request'.
     */
    private String requestArgumentsJson;

    /**
     * The JSON string representation of the data returned by the tool.
     * This is the 'response'.
     */
    private String responseDataJson;

    /**
     * The execution status of the tool call.
     * See {@link ToolCallStatus}.
     */
    private ToolCallStatus status;

    /**
     * The time it took for the tool to execute, in milliseconds.
     * This value is often extracted from the tool's response data.
     */
    private Long executionTimeMs;

    /**
     * Creates a new ToolCallData instance in a PENDING state.
     * This is typically used just before the tool is executed.
     *
     * @param toolName The name of the tool being called.
     * @param requestArgumentsJson The JSON arguments for the tool.
     */
    public ToolCallData(String toolName, String requestArgumentsJson) {
        this.toolName = toolName;
        this.requestArgumentsJson = requestArgumentsJson;
        this.status = ToolCallStatus.PENDING;
    }
}