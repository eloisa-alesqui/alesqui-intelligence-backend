package es.alesqui.intelligence.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * The root DTO for a user's full conversation history export.
 *
 * Contains metadata about the export itself (who, when) and the complete
 * list of conversations with their messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExportDTO {

    /**
     * The username of the user whose history was exported.
     */
    private String username;

    /**
     * The exact moment the export was generated.
     */
    private Instant exportedAt;

    /**
     * The total number of distinct conversations included in this export.
     */
    private int totalConversations;

    /**
     * The full list of conversations, each containing its messages.
     * Ordered from most recently updated to oldest.
     */
    private List<ConversationExportEntryDTO> conversations;
}
