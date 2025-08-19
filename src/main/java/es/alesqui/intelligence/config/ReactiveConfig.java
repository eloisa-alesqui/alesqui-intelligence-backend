package es.alesqui.intelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class ReactiveConfig {

    @Bean("chatScheduler")
    public Scheduler chatScheduler() {
        return Schedulers.newBoundedElastic(
            10,           
            1000,         
            "chat-pool"   
        );
    }
}
