package es.alesqui.intelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Configuration class for MongoDB-related settings.
 * This class defines a custom MongoConverter bean to handle specific MongoDB mapping requirements.
 */
@Configuration
public class MongoConfiguration {

    /**
     * Creates and configures a custom MongoConverter bean.
     * The MongoConverter is responsible for converting between Java objects and MongoDB documents.
     *
     * @param mongoMappingContext the MongoMappingContext used to manage MongoDB mappings
     * @return a configured instance of MongoConverter
     */
    @Bean
    public MongoConverter mongoConverter(MongoMappingContext mongoMappingContext) {
        // Create a MappingMongoConverter with a no-op DBRef resolver and the provided mapping context
        MappingMongoConverter converter = new MappingMongoConverter(
            NoOpDbRefResolver.INSTANCE, 
            mongoMappingContext
        );
        
        // Enable the use of the _class field for type discrimination
        converter.setTypeMapper(new DefaultMongoTypeMapper("_class"));
        
        // Replace dots in map keys with "__DOT__" to avoid conflicts with MongoDB's dot notation
        converter.setMapKeyDotReplacement("__DOT__");
        
        // Set custom conversions (empty in this case)
        converter.setCustomConversions(new MongoCustomConversions(java.util.Collections.emptyList()));
        
        // Finalize the converter setup
        converter.afterPropertiesSet();
        
        return converter;
    }
    
}
