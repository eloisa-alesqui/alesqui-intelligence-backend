package es.alesqui.intelligence.dto.conversation;

import es.alesqui.intelligence.model.conversation.ConversationStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Data Transfer Object (DTO) representing a summary view of a single
 * diagnostic ticket.
 * * This is used to populate the "Inbox" view in the IT Diagnostic Panel.
 */
@Data
@Builder
public class DiagnosticTicketDTO {

    /**
     * The unique ID of the specific ConversationRecord that this ticket refers to.
     */
    private String recordId;

    /**
     * The ID of the parent conversation this ticket belongs to.
     */
    private String conversationId;

    /**
     * The username of the user who initiated the conversation.
     */
    private String username;

    /**
     * The timestamp of when the original message was created.
     */
    private Instant timestamp;

    /**
     * The current status of this ticket (e.g., REPORTED_BY_USER, ERROR_PROCESSING).
     */
    private ConversationStatus status;

    /**
     * The user's original query or prompt for this turn of the conversation.
     */
    private String userPrompt; 
    
    /**
     * The optional comment provided by the user when they reported the issue.
     */
    private String userFeedbackComment;
}