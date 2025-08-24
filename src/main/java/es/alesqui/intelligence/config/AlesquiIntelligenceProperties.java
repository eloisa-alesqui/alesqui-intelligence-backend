package es.alesqui.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Alesqui Intelligence application.
 * 
 * This class maps the application-specific configuration properties
 * from application.properties to strongly-typed Java objects for
 * better type safety and IDE support.
 */
@Data
@Component
@ConfigurationProperties(prefix = "alesquiintelligence")
public class AlesquiIntelligenceProperties {

    private final Api api = new Api();
    private final Collection collection = new Collection();
    private final Ai ai = new Ai();
    private final Storage storage = new Storage();
    private final Http http = new Http();
    private final Validation validation = new Validation();

    @Data
    public static class Api {
        private int maxRequestsPerMinute = 100;
        private int maxCollectionSize = 1000;
        private String maxRequestSize = "10MB";
    }

    @Data
    public static class Collection {
        private long maxFileSize = 10485760L; // 10MB
        private long maxJsonSize = 10485760L; // 10MB
        private boolean enableDuplicateCheck = true;
        private int maxCollectionsPerUser = 50;
        private String allowedFileTypes = "json";
    }

    @Data
    public static class Ai {
        private boolean enabled = true;
        private int maxSuggestions = 5;
        private int suggestionCacheTtl = 3600;
    }

    @Data
    public static class Storage {
        private int maxHistoryEntries = 1000;
        private String cleanupInterval = "24h";
        private boolean backupEnabled = true;
        private String backupSchedule = "0 2 * * *";
    }

    @Data
    public static class Http {
        private int connectTimeout = 5000;
        private int readTimeout = 30000;
        private int writeTimeout = 30000;
    }

    @Data
    public static class Validation {
        private int maxNameLength = 255;
        private int maxDescriptionLength = 1000;
        private boolean requireCollectionName = true;
    }
    
    /**
     * Properties related to data storage, history, and file management.
     */
    @Data
    public static class FileStorage {
        /**
         * The base directory where temporary files (like generated Excels) are stored.
         * If not specified, the system's default temporary directory will be used.
         */
        private String tempUploadDir;
    }
}