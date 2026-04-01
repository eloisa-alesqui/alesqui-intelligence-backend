package es.alesqui.intelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "es.alesqui.intelligence.repository")
public class EmbeddedMongoConfig {

    @Bean
    public MongoClient mongoClient() {
        // Connect to the embedded MongoDB without authentication
        return MongoClients.create("mongodb://localhost:27017");
    }

    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "postmangpt");
    }
}
