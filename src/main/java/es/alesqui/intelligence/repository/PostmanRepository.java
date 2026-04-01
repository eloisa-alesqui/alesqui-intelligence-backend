package es.alesqui.intelligence.repository;

import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;

@Repository
public interface PostmanRepository extends ApiSpecRepository<PostmanDocument> {
}
