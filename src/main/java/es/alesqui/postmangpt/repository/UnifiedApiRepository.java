package es.alesqui.postmangpt.repository;

import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedApiRepository extends ReactiveMongoRepository<UnifiedApiDocument, String> {

	Optional<UnifiedApiDocument> findByName(String name);

	List<UnifiedApiDocument> findByTeam(String team);

	List<UnifiedApiDocument> findByTagsContaining(String tag);

	@Query("{ 'active': true }")
	List<UnifiedApiDocument> findAllActive();

	@Query("{ 'endpoints.path': ?0, 'endpoints.method': ?1 }")
	List<UnifiedApiDocument> findByEndpointPathAndMethod(String path, String method);

	@Query("{ 'endpoints.operationId': ?0 }")
	Optional<UnifiedApiDocument> findByOperationId(String operationId);
}
