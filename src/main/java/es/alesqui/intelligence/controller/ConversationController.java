package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * Retrieves a list of conversation summaries for the authenticated user.
     *
     * @return A reactive Flux of ConversationSummaryDTOs.
     */
    @GetMapping
    public Flux<ConversationSummaryDTO> getUserConversations() {
        // First, get the current username from the security context.
        // Then, use flatMapMany to pass it to the service method.
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
        // First, get the current username.
        // Then, use map to create the final response object, passing both
        // conversationId and username to the service method.
        return SecurityUtils.getCurrentUsername()
                .map(username -> ResponseEntity.ok(conversationService.getConversationDetailsForUser(conversationId, username)));
    }

}