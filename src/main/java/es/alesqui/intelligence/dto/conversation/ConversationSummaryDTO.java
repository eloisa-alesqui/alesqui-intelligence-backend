package es.alesqui.intelligence.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * A lightweight Data Transfer Object (DTO) that represents a brief summary
 * of a single conversation.
 *
 * This DTO is primarily used to populate a user's conversation history panel,
 * providing just enough information to identify a conversation without loading
 * its entire message history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDTO {

    /**
     * The unique, system-generated identifier for the conversation.
     * This ID is used to fetch the full conversation details when a user
     * selects it from their history.
     */
    private String conversationId;

    /**
     * A generated title for the conversation, typically derived from the first
     * user prompt to provide context at a glance.
     */
    private String title;

    /**
     * The timestamp of the most recent interaction (user or assistant message)
     * in the conversation. Used for sorting the history panel to show the most
     * recently active conversations first.
     */
    private Instant lastUpdated;
}