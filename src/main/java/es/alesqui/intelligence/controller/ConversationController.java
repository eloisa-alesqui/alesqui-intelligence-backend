package es.alesqui.intelligence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.conversation.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    // DTO for the request body
    public record ReportRequest(@Size(max = 2000, message = "comment must not exceed 2000 characters") String comment) {}

    /**
     * Exports the full conversation history of the authenticated user as a
     * downloadable JSON file.
     *
     * @return A Mono containing a ResponseEntity with the JSON bytes and
     * appropriate download headers.
     */
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportConversations() {
        return SecurityUtils.getCurrentUsername()
            .flatMap(conversationService::exportConversationsForUser)
            .flatMap(export -> Mono.fromCallable(() -> objectMapper.writeValueAsBytes(export)))
            .map(json -> {
                String filename = "conversations-" + LocalDate.now(ZoneOffset.UTC) + ".json";
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(json.length)
                    .body(json);
            });
    }

    /**
     * Retrieves a list of conversation summaries for the authenticated user.
     *
     * @return A reactive Flux of ConversationSummaryDTOs.
     */
    @GetMapping
    public Flux<ConversationSummaryDTO> getUserConversations() {
        return SecurityUtils.getCurrentUsername()
                .flatMapMany(conversationService::getHistoryForUser);
    }

    /**
     * Retrieves the full details of a specific conversation.
     *
     * @param conversationId The ID of the conversation to fetch.
     * @return A Mono containing a ResponseEntity with a Flux of all turns in the conversation.
     */
    @GetMapping("/{conversationId}")
    public Mono<ResponseEntity<Flux<ConversationDetailDTO>>> getConversationById(@PathVariable String conversationId) {
        return SecurityUtils.getCurrentUsername()
                .map(username -> ResponseEntity.ok(conversationService.getConversationDetailsForUser(conversationId, username)));
    }
    
    /**
     * Deletes a full conversation for the authenticated user.
     *
     * @param conversationId The ID of the conversation to delete.
     * @return A Mono with a ResponseEntity indicating success or failure.
     */
    @DeleteMapping("/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversation(@PathVariable String conversationId) {
        return SecurityUtils.getCurrentUsername()
            .flatMap(username -> conversationService.deleteConversationForUser(conversationId, username))
            .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT))) 
            .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    /**
     * Allows an authenticated user to report an issue with a specific
     * conversation record (message).
     */
    @PostMapping("/records/{recordId}/report")
    public Mono<ResponseEntity<String>> reportIssue(
            @PathVariable String recordId,
            @Valid @RequestBody ReportRequest request,
            Principal principal) {
        
        return conversationService.reportRecord(recordId, request.comment(), principal.getName())
            .map(updatedRecord -> ResponseEntity.ok("{\"message\": \"Thank you for your feedback. A report has been sent to the IT team.\" }"))
            .onErrorResume(SecurityException.class, e -> Mono.just(ResponseEntity.status(403).body("{\"error\": \"Forbidden\"}")))
            .onErrorResume(Exception.class, e -> Mono.just(ResponseEntity.status(404).body("{\"error\": \"Record not found\"}")));
    }

}