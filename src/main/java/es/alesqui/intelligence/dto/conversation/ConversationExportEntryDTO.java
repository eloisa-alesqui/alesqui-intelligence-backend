package es.alesqui.intelligence.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single conversation within a full conversation export.
 *
 * Groups the summary metadata of a conversation with its full list of
 * message exchanges, providing a self-contained unit for export purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExportEntryDTO {

    /**
     * The unique identifier of the conversation.
     */
    private String conversationId;

    /**
     * The generated title of the conversation, derived from the first user prompt.
     */
    private String title;

    /**
     * The timestamp of the most recent message in the conversation.
     */
    private Instant lastUpdated;

    /**
     * The full list of message exchanges (turns) within this conversation,
     * ordered chronologically.
     */
    private List<ConversationDetailDTO> messages;
}
