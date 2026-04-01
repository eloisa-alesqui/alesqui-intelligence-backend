package es.alesqui.intelligence.dto.chat.reasoning;

/**
 * Defines the different types of steps that can occur in an AI's
 * reasoning chain.
 */
public enum StepType {
    
    /**
     * Represents an internal thought or reasoning block of the AI.
     * This is the 'Reason' part of ReAct (e.g., "I need to call an API...").
     */
    THOUGHT,

    /**
     * Represents the 'Act' part of ReAct.
     * This step contains the details of one or more tools the AI
     * decided to execute.
     */
    TOOL_CALL,

    /**
     * Represents the final natural language response provided to the user
     * after all reasoning and tool calls are complete.
     */
    FINAL_RESPONSE
}