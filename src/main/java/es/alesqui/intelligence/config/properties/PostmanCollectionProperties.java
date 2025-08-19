package es.alesqui.intelligence.config.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "postman.collection")
public class PostmanCollectionProperties {

	@Value("${postman.collection.max-file-size:10485760}") // Default to 10MB
    private long maxFileSize;
	
	@Value("${postman.collection.max-json-size:10485760}") // Default to 10MB
    private long maxJsonSize;

}

