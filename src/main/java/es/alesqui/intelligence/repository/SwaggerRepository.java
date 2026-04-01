package es.alesqui.intelligence.repository;

import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;

@Repository
public interface SwaggerRepository extends ApiSpecRepository<SwaggerDocument> {
}
