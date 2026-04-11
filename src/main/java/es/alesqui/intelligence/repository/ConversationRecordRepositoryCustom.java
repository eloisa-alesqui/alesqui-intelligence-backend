package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import es.alesqui.intelligence.dto.conversation.TicketStatsDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface ConversationRecordRepositoryCustom {

    /**
     * Retrieves a summary of every distinct conversation belonging to the given user.
     *
     * Executes a MongoDB aggregation that groups all records by conversationId, picks
     * the first prompt as the conversation title, and computes the timestamp of the
     * most recent message. Results are returned sorted by last-updated time in
     * descending order, so the most recently active conversation appears first.
     *
     * @param username the authenticated username whose conversation history is requested.
     * @return a Flux emitting one ConversationSummaryDTO per distinct conversation,
     *         sorted from most to least recently updated.
     */
    Flux<ConversationSummaryDTO> findConversationSummariesByUsername(String username);

    /**
     * Counts the number of distinct conversations started by the given user.
     *
     * Executes a MongoDB aggregation that filters records by username, groups them
     * by conversationId to deduplicate, and then counts the resulting groups.
     * Returns 0 when the user has no recorded conversations.
     *
     * @param username the authenticated username to count conversations for.
     * @return a Mono emitting the total number of distinct conversations.
     */
    Mono<Long> countDistinctConversationsByUsername(String username);

    /**
     * Counts the number of distinct conversations started by the given user on or
     * after the specified point in time.
     *
     * Executes a MongoDB aggregation that filters records by both username and a
     * minimum timestamp, groups them by conversationId to deduplicate, and then
     * counts the resulting groups. Useful for computing activity windows such as
     * conversations started in the last 7 or 30 days.
     * Returns 0 when no matching records are found.
     *
     * @param username the authenticated username to count conversations for.
     * @param since    the inclusive lower bound for the record timestamp.
     * @return a Mono emitting the number of distinct conversations within the given window.
     */
    Mono<Long> countDistinctConversationsByUsernameAndTimestampAfter(String username, Instant since);

    /**
     * Retrieves a summary of the most recently updated conversation for the given user.
     *
     * Executes a MongoDB aggregation that groups all records by conversationId, picks
     * the first prompt as the conversation title, computes the timestamp of the most
     * recent message, and then returns only the top result after sorting by that
     * timestamp in descending order.
     * Emits an empty Mono when the user has no recorded conversations.
     *
     * @param username the authenticated username whose last conversation is requested.
     * @return a Mono emitting a LastConversationInfo for the most recently active
     *         conversation, or an empty Mono if none exists.
     */
    Mono<LastConversationInfo> findLastConversationByUsername(String username);

    /**
     * Counts tickets grouped by status for records created on or after the given instant.
     * Only REPORTED_BY_USER, ERROR_PROCESSING, UNDER_REVIEW, and RESOLVED statuses are
     * included; SUCCESS records are excluded as they represent normal conversations.
     *
     * @param since the inclusive lower bound for the record timestamp.
     * @return a Mono emitting a TicketStatsDTO with per-status counts.
     */
    Mono<TicketStatsDTO> countTicketsByStatusSince(Instant since);

    /**
     * Same as {@link #countTicketsByStatusSince(Instant)} but restricted to records
     * belonging to the given set of usernames. Used for group-filtered views where
     * non-SUPERADMIN users should only see tickets from users in their groups.
     *
     * @param since     the inclusive lower bound for the record timestamp.
     * @param usernames the set of usernames whose tickets are visible to the caller.
     * @return a Mono emitting a TicketStatsDTO with per-status counts.
     */
    Mono<TicketStatsDTO> countTicketsByStatusSinceAndUsernameIn(Instant since, List<String> usernames);
}
