package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.conversation.ConversationService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

}