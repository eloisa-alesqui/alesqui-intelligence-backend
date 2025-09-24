package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

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
}
