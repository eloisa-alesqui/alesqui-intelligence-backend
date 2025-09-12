package es.alesqui.intelligence.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.security.Principal;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/it-only")
    @PreAuthorize("hasRole('ROLE_IT')") 
    public Mono<String> getItMessage(Principal principal) {
        return Mono.just("Hello IT User: " + principal.getName() + "! You have access.");
    }

    @GetMapping("/business-only")
    @PreAuthorize("hasRole('ROLE_BUSINESS')")
    public Mono<String> getBusinessMessage(Principal principal) {
        return Mono.just("Hello Business User: " + principal.getName() + "! Welcome.");
    }

    @GetMapping("/any-user")
    @PreAuthorize("hasAnyRole('ROLE_IT', 'ROLE_BUSINESS')") 
    public Mono<String> getAnyUserMessage() {
        return Mono.just("This endpoint is accessible by any authenticated user.");
    }
}