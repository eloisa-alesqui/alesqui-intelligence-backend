package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.DiagnosticTicketDTO; 
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import es.alesqui.intelligence.service.conversation.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * Manages all API endpoints for the "Diagnostic and Support Panel".
 * * All endpoints in this controller are secured and require the user
 * to have 'ROLE_IT'.
 */
@RestController
@RequestMapping("/api/diagnostics")
@RequiredArgsConstructor
public class DiagnosticsController {

    private final ConversationService conversationService;
    
    /**
     * DTO record representing the request body for updating a ticket's status.
     *
     * @param status The new ConversationStatus to apply.
     */
    public record UpdateStatusRequest(ConversationStatus status) {}
    
    /**
     * DTO record representing the request body for adding an internal note.
     *
     * @param note The text content of the note. This field is validated
     * and cannot be blank.
     */
    public record AddNoteRequest(@NotBlank(message = "Note cannot be empty") String note) {}

    /**
     * Retrieves a paginated list of diagnostic tickets for the "Inbox" view.
     * * This list typically includes records reported by users or those that
     * resulted in an error, filtered by the provided statuses.
     *
     * @param statuses The list of ConversationStatus enums to filter by.
     * @param pageable Pagination information (page number, size, sort).
     * @return A Mono emitting a Page of diagnostic tickets.
     */
    @GetMapping("/tickets")
    public Mono<Page<DiagnosticTicketDTO>> getTickets(
            @RequestParam(defaultValue = "REPORTED_BY_USER,ERROR_PROCESSING") List<ConversationStatus> statuses,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "timestamp,desc") String sort) {
        
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = sortParams[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));
        
        return conversationService.getTickets(statuses, username, pageable);
    }

    /**
     * Retrieves the full, detailed history for a single conversation.
     * * This IT-specific view includes all messages, reasoning, user feedback,
     * and internal notes associated with the conversation.
     *
     * @param conversationId The unique ID of the conversation to fetch.
     * @return A Flux emitting all ConversationDetailDTOs for the conversation.
     */
    @GetMapping("/conversations/{conversationId}")
    public Flux<ConversationDetailDTO> getConversationDetails(
            @PathVariable String conversationId) {
        return conversationService.getConversationDetailsForIT(conversationId);
    }

    /**
     * Updates the status of a specific ticket (ConversationRecord).
     *
     * This endpoint is called by an IT user to change a ticket's status
     * (e.g., from 'REPORTED_BY_USER' to 'UNDER_REVIEW'). It returns the
     * full, updated DTO of the record.
     *
     * @param recordId The unique ID of the record to update, from the URL path.
     * @param request  The request body containing the new status.
     * @return A Mono emitting the updated ConversationDetailDTO.
     */
    @PutMapping("/tickets/{recordId}/status")
    public Mono<ConversationDetailDTO> updateTicketStatus(
            @PathVariable String recordId,
            @RequestBody UpdateStatusRequest request) {
        // Returns the Mono directly from the service
        return conversationService.updateRecordStatus(recordId, request.status());
    }

    /**
     * Adds a new internal note to a specific ticket (ConversationRecord).
     *
     * This endpoint is called by an IT user to add an audit note.
     * The request body is validated to ensure the note is not empty.
     * It returns the full, updated DTO including the new note.
     *
     * @param recordId The unique ID of the record to add a note to, from the URL path.
     * @param request  The validated request body containing the new note text.
     * @return A Mono emitting the updated ConversationDetailDTO.
     */
    @PostMapping("/tickets/{recordId}/notes")
    public Mono<ConversationDetailDTO> addInternalNote(
            @PathVariable String recordId,
            @RequestBody @Valid AddNoteRequest request) {
        // Returns the Mono directly from the service
        return conversationService.addInternalNote(recordId, request.note());
    }
       
}