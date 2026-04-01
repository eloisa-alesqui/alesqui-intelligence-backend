package es.alesqui.intelligence.dto.chat.reasoning;

/**
 * Represents the execution status of a tool call within a
 * {@link ToolCallData} object.
 */
public enum ToolCallStatus {
    
    /**
     * The tool call has been requested by the AI but has not
     * yet been executed.
     */
    PENDING,
    
    /**
     * The tool executed successfully.
     */
    SUCCESS,
    
    /**
     * The tool execution failed or returned an error.
     */
    ERROR
}