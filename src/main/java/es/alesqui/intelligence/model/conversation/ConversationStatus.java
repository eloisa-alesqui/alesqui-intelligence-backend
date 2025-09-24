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
     * The agent failed to get a valid response from a required external API.
     * This status is specific to failures during the tool execution step.
     */
    ERROR_API_CALL,

    /**
     * A general processing error occurred that was not related to a specific API call.
     * Examples include timeouts, classification failures, or internal exceptions.
     */
    ERROR_PROCESSING,

    /**
     * The user has flagged this response as incorrect or problematic.
     * This status acts as a trigger for the IT team to review.
     */
    REPORTED_BY_USER,

    /**
     * An IT team member is actively investigating the reported issue.
     */
    IN_REVIEW,

    /**
     * The reported issue has been investigated and resolved.
     */
    RESOLVED
}
