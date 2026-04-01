package es.alesqui.intelligence.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface ApiSpecRepository<D> extends ReactiveMongoRepository<D, String> {
    Mono<D> findByName(String name);
    Mono<Void> deleteByName(String name);
}
