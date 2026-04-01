package es.alesqui.intelligence.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.access.ApiGroupLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Mongo repository for {@link ApiGroupLink} documents.
 * Associates APIs to groups; APIs without links are considered public.
 */
@Repository
public interface ApiGroupLinkRepository extends ReactiveMongoRepository<ApiGroupLink, String> {

    /**
     * Lists all links for a given API id.
     * @param apiId API id.
     * @return Flux of links.
     */
    Flux<ApiGroupLink> findByApiId(String apiId);

    /**
     * Lists all links for a given group id.
     * @param groupId Group id.
     * @return Flux of links.
     */
    Flux<ApiGroupLink> findByGroupId(String groupId);

    /**
     * Deletes all links for a given group id.
     * @param groupId Group id.
     * @return Mono signalling completion.
     */
    Mono<Void> deleteByGroupId(String groupId);

    /**
     * Deletes all links for a given api id.
     * @param apiId API id.
     * @return Mono signalling completion.
     */
    Mono<Void> deleteByApiId(String apiId);

    /**
     * Checks if a link exists between an API and a group.
     * @param apiId API id.
     * @param groupId Group id.
     * @return Mono true if link exists.
     */
    Mono<Boolean> existsByApiIdAndGroupId(String apiId, String groupId);
}
