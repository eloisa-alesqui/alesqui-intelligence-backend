package es.alesqui.intelligence.service.conversation;

import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationExportDTO;
import es.alesqui.intelligence.dto.conversation.ConversationExportEntryDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.dto.conversation.DiagnosticTicketDTO;
import es.alesqui.intelligence.dto.conversation.LastConversationInfo;
import es.alesqui.intelligence.dto.conversation.TicketStatsDTO;
import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import es.alesqui.intelligence.repository.ConversationRecordRepository;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.identity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final GroupMembershipService groupMembershipService;
    private final UserService userService;

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
        ConversationRecord record = ConversationRecord.builder()
                .conversationId(request.getConversationId())
                .username(username)
                .timestamp(Instant.now())
                .userPrompt(request.getQuery())
                .responseText(response.getContent())
                .responseChart(response.getChart())
                .stepByStepReasoning(response.getReasoningSteps())
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
        ConversationRecord record = ConversationRecord.builder()
                .conversationId(request.getConversationId())
                .username(username)
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
        return repository.findConversationSummariesByUsername(username);
    }

    /**
     * Builds a full export of all conversations for the given user.
     *
     * Reuses {@link #getHistoryForUser(String)} for summaries and
     * {@link #getConversationDetailsForUser(String, String)} for each conversation's
     * messages, preserving ownership enforcement throughout.
     *
     * @param username The authenticated username whose history is exported.
     * @return A {@link Mono} emitting a {@link ConversationExportDTO} with all conversations.
     */
    public Mono<ConversationExportDTO> exportConversationsForUser(String username) {
        return getHistoryForUser(username)
            .concatMap(summary ->
                repository.findByConversationIdOrderByTimestampAsc(summary.getConversationId())
                    .map(this::mapRecordToExportDetail)
                    .collectList()
                    .map(messages -> ConversationExportEntryDTO.builder()
                        .conversationId(summary.getConversationId())
                        .title(summary.getTitle())
                        .lastUpdated(summary.getLastUpdated())
                        .messages(messages)
                        .build())
            )
            .collectList()
            .map(entries -> ConversationExportDTO.builder()
                .username(username)
                .exportedAt(Instant.now())
                .totalConversations(entries.size())
                .conversations(entries)
                .build());
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
            	.id(record.getId())
                .userPrompt(record.getUserPrompt())
                .responseText(record.getResponseText())
                .responseChart(record.getResponseChart())
                .stepByStepReasoning(record.getStepByStepReasoning())
                .timestamp(record.getTimestamp())
                .isError(record.getStatus() != ConversationStatus.SUCCESS)
                .userFeedbackComment(record.getUserFeedbackComment())
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
    
    /**
     * Deletes all records associated with a conversation, but only if the
     * specified user is the owner.
     *
     * @param conversationId The ID of the conversation to be deleted.
     * @param username The user requesting the deletion.
     * @return A {@link Mono<Void>} that completes when the deletion is done.
     * If the conversation doesn't belong to the user, it will emit an error
     * or complete empty to prevent unauthorized deletion.
     */
    public Mono<Void> deleteConversationForUser(String conversationId, String username) {
        return repository.findByConversationIdOrderByTimestampAsc(conversationId)
            .collectList() 
            .flatMap(records -> {
                if (records.isEmpty() || !records.get(0).getUsername().equals(username)) {
                    log.warn("🚨 User '{}' attempted to delete conversation '{}' but has no permission or it does not exist.", username, conversationId);
                    return Mono.empty();
                }
                log.info("🗑️ Deleting conversation '{}' for user '{}'.", conversationId, username);
                return repository.deleteByConversationId(conversationId);
            });
    }
    
    /**
     * Marks a specific record as REPORTED_BY_USER, but only if
     * the user owns it.
     *
     * @param recordId The ID of the ConversationRecord to report.
     * @param comment The user's optional feedback.
     * @param username The authenticated user.
     * @return A Mono of the updated record, or an error.
     */
    public Mono<ConversationRecord> reportRecord(String recordId, String comment, String username) {
        return repository.findById(recordId)
            .flatMap(record -> {
                if (!record.getUsername().equals(username)) {
                    log.warn("🚨 User '{}' attempted to report record '{}' owned by '{}'.", username, recordId, record.getUsername());
                    return Mono.error(new SecurityException("User does not own this record."));
                }
                
                record.setStatus(ConversationStatus.REPORTED_BY_USER);
                record.setUserFeedbackComment(comment);
                log.info("🚩 Record '{}' reported by user '{}' with comment: {}", recordId, username, comment);
                return repository.save(record);
            })
            .switchIfEmpty(Mono.error(new RuntimeException("Record not found")));
    }

    /**
     * Retrieves a paginated list of diagnostic tickets based on their status.
     * * This method is designed to populate the "Inbox" view for the IT
     * Diagnostic Panel.
     * * If the caller is not a SUPERADMIN, the results are filtered to only show
     * tickets from users who belong to the same groups as the caller.
     * * SUPERADMIN users can see all tickets regardless of group membership.
     * * It performs two database queries: one to fetch the paginated data and
     * another to get the total count for pagination metadata.
     *
     * @param statuses A list of ConversationStatus enums to filter by.
     * @param username The username string to filter by (case-insensitive contains).
     * @param pageable Pagination and sorting information provided by Spring Data.
     * @return A Mono emitting a Page containing the list of DiagnosticTicketDTOs
     * and pagination details.
     */
    public Mono<Page<DiagnosticTicketDTO>> getTickets(List<ConversationStatus> statuses, String username, Pageable pageable) {
        
        // First, get the current user and check if they are a SUPERADMIN
        return userService.getCurrentUser()
            .flatMap(currentUser -> {
                boolean isSuperAdmin = currentUser.getRoles() != null && 
                    currentUser.getRoles().contains(es.alesqui.intelligence.model.core.enums.Role.ROLE_SUPERADMIN);
                
                if (isSuperAdmin) {
                    // SUPERADMIN can see all tickets
                    return executeTicketsQuery(statuses, username, pageable, null);
                } else {
                    // Non-SUPERADMIN: filter by group membership
                    return filterTicketsByGroups(currentUser.getId(), statuses, username, pageable);
                }
            });
    }
    
    /**
     * Filters tickets to only show those from users who share at least one group
     * with the calling user.
     */
    private Mono<Page<DiagnosticTicketDTO>> filterTicketsByGroups(
            String currentUserId, 
            List<ConversationStatus> statuses, 
            String username, 
            Pageable pageable) {
        
        // 1. Get all groups the current user belongs to (via GroupMembershipService)
        return groupMembershipService.getGroupIdsByUserId(currentUserId)
            .collectList()
            .flatMap(currentUserGroupIds -> {
                if (currentUserGroupIds.isEmpty()) {
                    // User belongs to no groups, return empty result
                    log.debug("User {} has no group memberships, returning empty ticket list", currentUserId);
                    return Mono.just(PageableExecutionUtils.getPage(List.of(), pageable, () -> 0L));
                }
                
                // 2. Get all users who belong to at least one of those groups (via GroupMembershipService)
                return groupMembershipService.getUserIdsByGroupIds(currentUserGroupIds)
                    .collectList()
                    .flatMap(groupMemberUserIds -> {
                        if (groupMemberUserIds.isEmpty()) {
                            // No other users in these groups
                            log.debug("No users found in groups {}, returning empty ticket list", currentUserGroupIds);
                            return Mono.just(PageableExecutionUtils.getPage(List.of(), pageable, () -> 0L));
                        }
                        
                        // 3. Convert user IDs to usernames using UserService (proper layer separation)
                        return userService.getUsernamesByIds(groupMemberUserIds)
                            .collectList()
                            .flatMap(allowedUsernames -> {
                                if (allowedUsernames.isEmpty()) {
                                    log.debug("No usernames found for user IDs {}", groupMemberUserIds);
                                    return Mono.just(PageableExecutionUtils.getPage(List.of(), pageable, () -> 0L));
                                }
                                
                                log.debug("User {} can see tickets from {} users in shared groups", 
                                    currentUserId, allowedUsernames.size());
                                
                                // 4. Execute the query with the allowed usernames
                                return executeTicketsQuery(statuses, username, pageable, allowedUsernames);
                            });
                    });
            });
    }
    
    /**
     * Executes the actual database query to fetch tickets.
     * @param allowedUsernames If not null, restricts results to these usernames (group filtering).
     */
    private Mono<Page<DiagnosticTicketDTO>> executeTicketsQuery(
            List<ConversationStatus> statuses, 
            String usernameSearch, 
            Pageable pageable,
            List<String> allowedUsernames) {
        
        Flux<ConversationRecord> pageQuery;
        Mono<Long> countQuery;
        
        // Determine which repository method to use based on filters
        if (allowedUsernames == null) {
            // SUPERADMIN path: no group filtering
            if (StringUtils.isBlank(usernameSearch)) {
                pageQuery = repository.findByStatusInOrderByTimestampDesc(statuses, pageable);
                countQuery = repository.countByStatusIn(statuses);
            } else {
                pageQuery = repository.findByStatusInAndUsernameContainsIgnoreCaseOrderByTimestampDesc(
                    statuses, usernameSearch, pageable);
                countQuery = repository.countByStatusInAndUsernameContainsIgnoreCase(statuses, usernameSearch);
            }
        } else {
            // Group-filtered path
            if (StringUtils.isBlank(usernameSearch)) {
                pageQuery = repository.findByStatusInAndUsernameInOrderByTimestampDesc(
                    statuses, allowedUsernames, pageable);
                countQuery = repository.countByStatusInAndUsernameIn(statuses, allowedUsernames);
            } else {
                pageQuery = repository.findByStatusInAndUsernameInAndUsernameContainsIgnoreCaseOrderByTimestampDesc(
                    statuses, allowedUsernames, usernameSearch, pageable);
                countQuery = repository.countByStatusInAndUsernameInAndUsernameContainsIgnoreCase(
                    statuses, allowedUsernames, usernameSearch);
            }
        }
        
        // Map records to DTOs
        Flux<DiagnosticTicketDTO> ticketsFlux = pageQuery
            .map(record -> DiagnosticTicketDTO.builder()
                .recordId(record.getId())
                .conversationId(record.getConversationId())
                .username(record.getUsername())
                .timestamp(record.getTimestamp())
                .status(record.getStatus())
                .userPrompt(record.getUserPrompt())
                .userFeedbackComment(record.getUserFeedbackComment())
                .build()
            );
        
        return Mono.zip(ticketsFlux.collectList(), countQuery)
            .map((Tuple2<List<DiagnosticTicketDTO>, Long> tuple) -> { 
                List<DiagnosticTicketDTO> list = tuple.getT1();
                long totalCount = tuple.getT2(); 
                return PageableExecutionUtils.getPage(list, pageable, () -> totalCount);
            });
    }

    /**
     * Retrieves the full, detailed history of a conversation for an IT user.
     * * This method bypasses the user ownership check and includes sensitive
     * information like internal notes, making it suitable only for the
     * diagnostic panel.
     *
     * @param conversationId The unique ID of the conversation to retrieve.
     * @return A Flux emitting all ConversationDetailDTOs for the specified
     * conversation, ordered by timestamp.
     */
    public Flux<ConversationDetailDTO> getConversationDetailsForIT(String conversationId) {
        return repository.findByConversationIdOrderByTimestampAsc(conversationId)
            .map(record -> ConversationDetailDTO.builder()
                .id(record.getId())
                .userPrompt(record.getUserPrompt())
                .responseText(record.getResponseText())
                .responseChart(record.getResponseChart())
                .stepByStepReasoning(record.getStepByStepReasoning())
                .timestamp(record.getTimestamp())
                .isError(record.getStatus() != ConversationStatus.SUCCESS)
                .userFeedbackComment(record.getUserFeedbackComment())
                .internalNotes(record.getInternalNotes()) // <-- Include notes for IT
                .build()
            );
    }

    /**
     * Updates the status of a specific ConversationRecord (ticket).
     *
     * This action is performed by an IT user. Upon successful update,
     * the modified record is returned as a detailed DTO.
     *
     * @param recordId The unique ID of the ConversationRecord to update.
     * @param newStatus The new ConversationStatus to apply.
     * @return A Mono emitting the updated ConversationDetailDTO.
     */
    public Mono<ConversationDetailDTO> updateRecordStatus(String recordId, ConversationStatus newStatus) {
        return repository.findById(recordId)
            .flatMap(record -> {
                record.setStatus(newStatus);
                log.info("🔄 Record '{}' status updated to {} by IT.", recordId, newStatus);
                return repository.save(record);
            })
            .map(this::mapRecordToDetailDTO); // Map to DTO before returning
    }

    /**
     * Adds a new internal note to a specific ConversationRecord (ticket).
     *
     * The note is automatically prepended with the current user and timestamp
     * for auditing purposes.
     *
     * This action is performed by an IT user. Upon successful update,
     * the modified record is returned as a detailed DTO.
     *
     * @param recordId The unique ID of the ConversationRecord to add a note to.
     * @param note The text content of the note to add.
     * @return A Mono emitting the updated ConversationDetailDTO.
     */
    public Mono<ConversationDetailDTO> addInternalNote(String recordId, String note) {
        
        // 1. Get the username reactively from the security context
        Mono<String> usernameMono = SecurityUtils.getCurrentUsername()
                .switchIfEmpty(Mono.just("system")); // Fallback just in case

        // 2. Combine the repository call and username mono
        return Mono.zip(repository.findById(recordId), usernameMono)
            .flatMap(tuple -> {
                ConversationRecord record = tuple.getT1();
                String username = tuple.getT2();

                if (record.getInternalNotes() == null) {
                    record.setInternalNotes(new ArrayList<>());
                }
                
                // 3. Format the note with the correct username
                String formattedNote = String.format("[%s @ %s]: %s", 
                    username, 
                    Instant.now().toString(), 
                    note
                );
                
                record.getInternalNotes().add(formattedNote);
                log.info("📝 Note added to record '{}' by IT.", recordId);
                return repository.save(record);
            })
            .map(this::mapRecordToDetailDTO); // Map to DTO before returning
    }
    
    /**
     * Maps a ConversationRecord to a ConversationDetailDTO for user-facing export.
     * Does not include internalNotes.
     */
    private ConversationDetailDTO mapRecordToExportDetail(ConversationRecord record) {
        return ConversationDetailDTO.builder()
            .id(record.getId())
            .userPrompt(record.getUserPrompt())
            .responseText(record.getResponseText())
            .responseChart(record.getResponseChart())
            .stepByStepReasoning(record.getStepByStepReasoning())
            .timestamp(record.getTimestamp())
            .isError(record.getStatus() != ConversationStatus.SUCCESS)
            .userFeedbackComment(record.getUserFeedbackComment())
            .build();
    }

    /**
     * Private helper method to convert a ConversationRecord entity into a
     * ConversationDetailDTO.
     *
     * This DTO mapping is intended for IT users, as it includes sensitive
     * fields such as internalNotes.
     *
     * @param record The ConversationRecord entity from the database.
     * @return A ConversationDetailDTO populated with the record's data.
     */
    private ConversationDetailDTO mapRecordToDetailDTO(ConversationRecord record) {
        return ConversationDetailDTO.builder()
            .id(record.getId())
            .userPrompt(record.getUserPrompt())
            .responseText(record.getResponseText())
            .responseChart(record.getResponseChart())
            .stepByStepReasoning(record.getStepByStepReasoning())
            .timestamp(record.getTimestamp())
            .isError(record.getStatus() != ConversationStatus.SUCCESS)
            .userFeedbackComment(record.getUserFeedbackComment())
            .internalNotes(record.getInternalNotes())
            .build();
    }

    /**
     * Counts the total number of interaction records saved for the given user,
     * regardless of conversation or status. Each record represents a single
     * user prompt and its corresponding response.
     *
     * @param username the authenticated username to count records for.
     * @return a Mono emitting the total number of messages sent by the user.
     */
    public Mono<Long> countMessagesByUsername(String username) {
        return repository.countByUsername(username);
    }

    /**
     * Counts the number of interaction records for the given user that include
     * a chart in the response. This reflects how many times the AI produced a
     * data visualization for this user.
     *
     * @param username the authenticated username to count chart responses for.
     * @return a Mono emitting the number of responses that contained a chart.
     */
    public Mono<Long> countChartsByUsername(String username) {
        return repository.countByUsernameAndResponseChartIsNotNull(username);
    }

    /**
     * Counts the number of distinct conversations started by the given user.
     * Multiple interaction records sharing the same conversationId are counted
     * as a single conversation.
     *
     * @param username the authenticated username to count conversations for.
     * @return a Mono emitting the total number of distinct conversations.
     */
    public Mono<Long> countDistinctConversations(String username) {
        return repository.countDistinctConversationsByUsername(username);
    }

    /**
     * Counts the number of distinct conversations started by the given user on
     * or after the specified point in time. Useful for computing activity windows
     * such as conversations initiated in the last 7 or 30 days.
     *
     * @param username the authenticated username to count conversations for.
     * @param since    the inclusive lower bound for the record timestamp.
     * @return a Mono emitting the number of distinct conversations within the given window.
     */
    public Mono<Long> countDistinctConversationsSince(String username, Instant since) {
        return repository.countDistinctConversationsByUsernameAndTimestampAfter(username, since);
    }

    /**
     * Retrieves a summary of the most recently updated conversation for the given user,
     * including its identifier, title, and last-updated timestamp.
     * Emits an empty Mono when the user has no recorded conversations.
     *
     * @param username the authenticated username whose last conversation is requested.
     * @return a Mono emitting a LastConversationInfo for the most recently active
     *         conversation, or an empty Mono if none exists.
     */
    public Mono<LastConversationInfo> getLastConversation(String username) {
        return repository.findLastConversationByUsername(username);
    }

    /**
     * Counts open support tickets visible to the current user.
     * A ticket is considered open when its status is either REPORTED_BY_USER or
     * ERROR_PROCESSING. For SUPERADMIN users all tickets are included; for other
     * roles only tickets from users who share at least one group with the caller
     * are counted, mirroring the filtering logic applied by {@link #getTickets}.
     *
     * @return a Mono emitting the number of open tickets pending IT review.
     */
    public Mono<Long> countOpenTickets() {
        List<ConversationStatus> openStatuses =
            List.of(ConversationStatus.REPORTED_BY_USER, ConversationStatus.ERROR_PROCESSING);

        return userService.getCurrentUser()
            .flatMap(currentUser -> {
                boolean isSuperAdmin = currentUser.getRoles() != null &&
                    currentUser.getRoles().contains(es.alesqui.intelligence.model.core.enums.Role.ROLE_SUPERADMIN);

                if (isSuperAdmin) {
                    return repository.countByStatusIn(openStatuses);
                }
                return groupMembershipService.getGroupIdsByUserId(currentUser.getId())
                    .collectList()
                    .flatMap(groupIds -> {
                        if (groupIds.isEmpty()) {
                            return Mono.just(0L);
                        }
                        return groupMembershipService.getUserIdsByGroupIds(groupIds)
                            .collectList()
                            .flatMap(userIds -> {
                                if (userIds.isEmpty()) {
                                    return Mono.just(0L);
                                }
                                return userService.getUsernamesByIds(userIds)
                                    .collectList()
                                    .flatMap(usernames -> {
                                        if (usernames.isEmpty()) {
                                            return Mono.just(0L);
                                        }
                                        return repository.countByStatusInAndUsernameIn(openStatuses, usernames);
                                    });
                            });
                    });
            });
    }

    /**
     * Returns ticket counts grouped by status for the last 30 days.
     * For SUPERADMIN users all tickets are included; for other roles only tickets
     * from users who share at least one group with the caller are counted,
     * mirroring the filtering logic applied by {@link #getTickets}.
     * SUCCESS records are excluded as they represent normal conversations.
     *
     * @return a Mono emitting a TicketStatsDTO with per-status counts.
     */
    public Mono<TicketStatsDTO> getTicketStats() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        return userService.getCurrentUser()
            .flatMap(currentUser -> {
                boolean isSuperAdmin = currentUser.getRoles() != null &&
                    currentUser.getRoles().contains(es.alesqui.intelligence.model.core.enums.Role.ROLE_SUPERADMIN);

                if (isSuperAdmin) {
                    return repository.countTicketsByStatusSince(since);
                }
                return groupMembershipService.getGroupIdsByUserId(currentUser.getId())
                    .collectList()
                    .flatMap(groupIds -> {
                        if (groupIds.isEmpty()) {
                            return Mono.just(new TicketStatsDTO(0L, 0L, 0L, 0L));
                        }
                        return groupMembershipService.getUserIdsByGroupIds(groupIds)
                            .collectList()
                            .flatMap(userIds -> {
                                if (userIds.isEmpty()) {
                                    return Mono.just(new TicketStatsDTO(0L, 0L, 0L, 0L));
                                }
                                return userService.getUsernamesByIds(userIds)
                                    .collectList()
                                    .flatMap(usernames -> {
                                        if (usernames.isEmpty()) {
                                            return Mono.just(new TicketStatsDTO(0L, 0L, 0L, 0L));
                                        }
                                        return repository.countTicketsByStatusSinceAndUsernameIn(since, usernames);
                                    });
                            });
                    });
            });
    }

}