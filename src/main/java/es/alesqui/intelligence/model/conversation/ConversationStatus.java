package es.alesqui.intelligence.model.conversation;

/**
 * Defines the possible statuses of a ConversationRecord.
 * This is used to track the state of an interaction, especially for the support and diagnostics workflow.
 */
public enum ConversationStatus {

    /**
     * The interaction was processed successfully without any known errors.
     */
    SUCCESS,

    /**
     * A general processing error occurred.
     * Examples include timeouts, internal exceptions, ...
     */
    ERROR_PROCESSING,

    /**
     * The user has flagged this specific record for review.
     */
    REPORTED_BY_USER,
    
    /**
     * An IT team member is actively investigating this report.
     */
    UNDER_REVIEW,
    
    /**
     * The investigation is complete and the issue is closed.
     */
    RESOLVED
}
