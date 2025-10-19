package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.model.conversation.ConversationStatus;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data Reactive MongoDB repository for the {@link ConversationRecord} document.
 *
 * This interface provides a non-blocking, reactive API for all database
 * operations related to conversation history. By extending ReactiveMongoRepository,
 * it inherits methods like save, findById, findAll, etc., which return
 * reactive types (Mono and Flux).
 */
@Repository
public interface ConversationRecordRepository extends ReactiveMongoRepository<ConversationRecord, String> {

    /**
     * Finds all interaction records belonging to a single conversation, ordered by timestamp.
     *
     * @param conversationId The unique identifier for the conversation.
     * @return A {@link Flux} that emits all {@link ConversationRecord} objects for the given ID,
     * sorted chronologically.
     */
    Flux<ConversationRecord> findByConversationIdOrderByTimestampAsc(String conversationId);

    /**
     * Finds all interaction records initiated by a specific user, ordered by most recent first.
     * This is used to build the conversation history for a user.
     *
     * @param username The username to search for.
     * @return A {@link Flux} that emits all {@link ConversationRecord} objects for the given user,
     * sorted in descending order of time.
     */
    Flux<ConversationRecord> findByUsernameOrderByTimestampDesc(String username);

    /**
     * Finds all interaction records that have a specific status.
     * This is crucial for the IT support panel to find, for example, all records
     * flagged with REPORTED_BY_USER.
     *
     * @param status The {@link ConversationStatus} to filter by.
     * @return A {@link Flux} that emits all matching {@link ConversationRecord} objects.
     */
    Flux<ConversationRecord> findByStatus(ConversationStatus status);
    
    /**
     * Deletes all records associated with a given conversation ID.
     *
     * @param conversationId The unique identifier for the conversation.
     * @return A {@link Mono<Void>} that completes once the deletion operation is finished.
     */
    Mono<Void> deleteByConversationId(String conversationId);
    
    /**
    * Finds all ConversationRecords that match any of the provided statuses,
    * ordered by timestamp in descending order, and returns them as a paginated Flux.
    * * This query is used to populate the main list for the IT "Inbox" view.
    *
    * @param statuses A list of ConversationStatus enums to filter by.
    * @param pageable A Pageable object containing pagination (page, size) and
    * sorting information.
    * @return A Flux emitting the ConversationRecords for the requested page.
    */
    Flux<ConversationRecord> findByStatusInOrderByTimestampDesc(List<ConversationStatus> statuses, Pageable pageable);


    /**
    * Counts the total number of ConversationRecords that match any of the
    * provided statuses.
    * * This query is used to calculate the total number of pages required
    * for pagination in the IT "Inbox" view.
    *
    * @param statuses A list of ConversationStatus enums to count.
    * @return A Mono emitting the total count as a Long.

    */

    Mono<Long> countByStatusIn(List<ConversationStatus> statuses);

    /**
     * Finds all ConversationRecords that match any of the provided statuses
     * AND contain the given username (case-insensitive), ordered by timestamp descending.
     * * This query is used to populate the IT "Inbox" view when a username filter is applied.
     *
     * @param statuses A list of ConversationStatus enums to filter by.
     * @param username The username string to filter by (case-insensitive contains).
     * @param pageable A Pageable object containing pagination (page, size) and
     * sorting information.
     * @return A Flux emitting the ConversationRecords for the requested page.
     */
    Flux<ConversationRecord> findByStatusInAndUsernameContainsIgnoreCaseOrderByTimestampDesc(
            List<ConversationStatus> statuses, String username, Pageable pageable);
    
    /**
     * Counts the total number of ConversationRecords that match any of the
     * provided statuses AND contain the given username (case-insensitive).
     * * This query is used to calculate the total number of pages required
     * for pagination in the IT "Inbox" view when a username filter is applied.
     *
     * @param statuses A list of ConversationStatus enums to count.
     * @param username The username string to count by (case-insensitive contains).
     * @return A Mono emitting the total count as a Long.
     */
    Mono<Long> countByStatusInAndUsernameContainsIgnoreCase(
            List<ConversationStatus> statuses, String username);
}
