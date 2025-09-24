package es.alesqui.intelligence.service.conversation;

import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import es.alesqui.intelligence.repository.ConversationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for managing the persistence of conversation records.
 *
 * This service provides a reactive, non-blocking API to save, retrieve,
 * and update chat interactions in the MongoDB database. It acts as an
 * intermediary between the chat orchestration layer and the data repository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRecordRepository repository;

    /**
     * Saves a complete user interaction (request and response) to the database.
     *
     * This method captures all relevant details for auditing, debugging, and user history.
     * The username is passed directly from the orchestration layer, which has access
     * to the security context.
     *
     * @param request  The original ChatRequest from the user.
     * @param response The final ChatResponse sent to the user.
     * @param username The authenticated username of the user performing the action.
     * @return A {@link Mono} that completes once the save operation is finished.
     * It emits the saved ConversationRecord.
     */
    public Mono<ConversationRecord> saveInteraction(ChatRequest request, ChatResponse response, String username) {
        // Lógica simplificada: ya no necesitamos el flatMap
        ConversationRecord record = ConversationRecord.builder()
                .conversationId(request.getConversationId())
                .username(username)
                .timestamp(Instant.now())
                .userPrompt(request.getQuery())
                .responseText(response.getContent())
                .responseChart(response.getChart())
                .stepByStepReasoning(response.getReasoning())
                .status(response.isSuccess() ? ConversationStatus.SUCCESS : ConversationStatus.ERROR_PROCESSING)
                .build();

        return repository.save(record)
                .doOnSuccess(savedRecord -> log.info("💾 Interaction saved for user '{}'. Record ID: {}", username, savedRecord.getId()))
                .doOnError(error -> log.error("🚨 Failed to save interaction for user '{}'", username, error));
    }
    
    /**
     * Saves a FAILED user interaction to the database.
     *
     * This method is called when the main processing chain fails, capturing the
     * user's request and the exception for later analysis by the IT team.
     *
     * @param request The original ChatRequest from the user.
     * @param error The Throwable that caused the failure.
     * @param username The authenticated username of the user who initiated the request.
     * @return A {@link Mono} that completes once the save operation is finished.
     */
    public Mono<Void> saveFailedInteraction(ChatRequest request, Throwable error, String username) {
        // Lógica simplificada: ya no necesitamos el flatMap
        ConversationRecord record = ConversationRecord.builder()
                .conversationId(request.getConversationId())
                .username(username) // Usamos el username que nos pasan
                .timestamp(Instant.now())
                .userPrompt(request.getQuery())
                .responseText("Error: " + error.getMessage())
                .responseChart(null)
                .stepByStepReasoning(null)
                .status(ConversationStatus.ERROR_PROCESSING)
                .build();

        return repository.save(record)
                .doOnSuccess(savedRecord -> log.warn("💾 Failed interaction saved for user '{}'. Record ID: {}", username, savedRecord.getId()))
                .doOnError(saveError -> log.error("🚨 CRITICAL: Failed to save a FAILED interaction for user '{}'", username, saveError))
                .then();
    }
    
    /**
     * Retrieves the conversation history for a specific user.
     *
     * This method fetches all records for the given user, groups them by
     * conversationId, and constructs a summary object for each conversation,
     * containing its title and last update time.
     *
     * @param username The username for whom to retrieve the conversation history.
     * @return A Flux emitting a summary DTO for each distinct conversation,
     * sorted with the most recently updated first.
     */
    public Flux<ConversationSummaryDTO> getHistoryForUser(String username) {
        return repository.findByUsernameOrderByTimestampDesc(username)
            // Group all records by their conversationId into a Mono<Map<String, Collection<ConversationRecord>>>
            .collectMultimap(ConversationRecord::getConversationId)
            // Switch from Mono to Flux, where each item is one conversation (Map.Entry)
            .flatMapMany(groupedMap -> Flux.fromIterable(groupedMap.entrySet()))
            // Map each conversation entry to its summary DTO
            .map(entry -> {
                // The entry key is the conversationId, the value is a Collection of records
                List<ConversationRecord> convRecords = new ArrayList<>(entry.getValue());

                // The first message is the best title
                String title = convRecords.stream()
                    .min(Comparator.comparing(ConversationRecord::getTimestamp))
                    .map(ConversationRecord::getUserPrompt)
                    .orElse("Untitled Conversation");

                // The last interaction time determines the sorting order
                Instant lastUpdated = convRecords.stream()
                    .max(Comparator.comparing(ConversationRecord::getTimestamp))
                    .map(ConversationRecord::getTimestamp)
                    .orElse(Instant.now());

                return new ConversationSummaryDTO(entry.getKey(), title, lastUpdated);
            })
            // Sort the resulting summaries by their last update time, descending
            .sort(Comparator.comparing(ConversationSummaryDTO::getLastUpdated).reversed());
    }

    /**
     * Retrieves the full details of a specific conversation for a given user.
     *
     * This method ensures that the requested conversation belongs to the specified
     * user, providing a layer of authorization before returning the full exchange.
     *
     * @param conversationId The ID of the conversation to retrieve.
     * @param username The username of the user requesting the conversation, used for verification.
     * @return A Flux emitting a detail DTO for each turn in the conversation, ordered chronologically.
     * If the conversation does not belong to the user, the Flux will be empty.
     */
    public Flux<ConversationDetailDTO> getConversationDetailsForUser(String conversationId, String username) {
        return repository.findByConversationIdOrderByTimestampAsc(conversationId)
            // Authorize access: ensure the user owns this conversation
            .filter(record -> username.equals(record.getUsername()))
            // Map the database record to the DTO
            .map(record -> ConversationDetailDTO.builder()
                .userPrompt(record.getUserPrompt())
                .responseText(record.getResponseText())
                .responseChart(record.getResponseChart())
                .stepByStepReasoning(record.getStepByStepReasoning() != null ? record.getStepByStepReasoning().toString() : null)
                .timestamp(record.getTimestamp())
                .isError(record.getStatus() != ConversationStatus.SUCCESS)
                .build()
            );
    }
    
    /**
     * Finds all conversation records for a given conversation ID, sorted by timestamp.
     * This is intended for internal use, like populating chat memory.
     *
     * @param conversationId The conversation ID.
     * @return A Flux of ConversationRecord.
     */
    public Flux<ConversationRecord> findAllByConversationIdForMemory(String conversationId) {
        return repository.findByConversationIdOrderByTimestampAsc(conversationId);
    }

}