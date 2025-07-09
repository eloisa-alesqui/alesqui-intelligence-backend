package es.alesqui.postmangpt.config.mapper;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for Jackson ObjectMapper beans.
 * 
 * This configuration provides specialized ObjectMapper instances for different data formats:
 * - A primary JSON ObjectMapper for standard JSON processing
 * - A dedicated YAML ObjectMapper for YAML file parsing and generation
 * 
 * The YAML ObjectMapper is configured with specific features to ensure clean YAML output
 * without document start markers and with minimized quotes for better readability.
 * 
 * Usage:
 * - Inject the primary ObjectMapper for JSON operations
 * - Use @Qualifier("yamlObjectMapper") to inject the YAML-specific mapper
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class ObjectMapperConfig {
	
	private final ApplicationContext applicationContext;

    /**
     * Creates and configures the primary JSON ObjectMapper bean.
     * 
     * This ObjectMapper is configured with default settings and is marked as @Primary,
     * making it the default choice for dependency injection when no specific qualifier
     * is provided. It handles standard JSON serialization and deserialization operations.
     * 
     * @return ObjectMapper configured for JSON processing
     */
    @Bean
    @Primary
    public ObjectMapper jsonObjectMapper() {
        // Create standard ObjectMapper with default JSON configuration
    	ObjectMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
    	log.info("Created JSON ObjectMapper with factory: {}", mapper.getFactory().getClass().getName());
        return mapper;
    }

    /**
     * Creates and configures a specialized YAML ObjectMapper bean.
     * This mapper should be used when parsing OpenAPI YAML files or generating YAML content.
     * Access this bean using @Qualifier("yamlObjectMapper") annotation.
     * 
     * @return ObjectMapper configured for YAML processing with optimized settings
     */
    @Bean
    @Qualifier("yamlObjectMapper")
    public ObjectMapper yamlObjectMapper() {
    	ObjectMapper mapper = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
    	log.info("Created YAML ObjectMapper with factory: {}", mapper.getFactory().getClass().getName());
        return mapper;
    }
    
    /**
     * Logs all ObjectMapper beans registered in the Spring context to verify their configuration.
     */
    @PostConstruct
    public void listObjectMappers() {
        Map<String, ObjectMapper> mappers = applicationContext.getBeansOfType(ObjectMapper.class);
        mappers.forEach((name, mapper) -> 
            log.info("ObjectMapper bean: {} -> {}", name, mapper.getFactory().getClass().getName())
        );
    }
}
