package es.alesqui.intelligence.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {

	@GetMapping("/hello")
    public Mono<String> sayHello() {
        log.info("--- 🚨🚨🚨 TEST CONTROLLER EXECUTED ---");
        return Mono.just("Hello World!");
    }
}