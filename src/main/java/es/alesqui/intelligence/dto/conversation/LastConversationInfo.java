package es.alesqui.intelligence.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * A lightweight Data Transfer Object that carries the key details of a user's
 * most recently active conversation.
 *
 * This DTO is returned by the custom repository aggregation that finds the
 * single conversation with the latest message timestamp for a given user.
 * It is intended for Dashboard widgets that need to surface a quick summary
 * of the last activity without loading the full conversation history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastConversationInfo {

    /**
     * The unique identifier of the conversation, as stored in the conversationId
     * field of each ConversationRecord. Can be used to navigate to the full
     * conversation detail view.
     */
    private String conversationId;

    /**
     * A short title derived from the first user prompt in the conversation.
     * Provides enough context to identify the subject of the conversation at a glance.
     */
    private String title;

    /**
     * The timestamp of the most recent message in the conversation, stored in UTC.
     * Represents when the conversation was last active.
     */
    private Instant lastUpdated;
}
