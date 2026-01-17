package es.alesqui.intelligence.model.audit;

/**
 * Enumeration of entity types that can be audited in the system.
 * 
 * Each value represents a distinct resource type that administrative
 * actions can be performed upon. This allows audit logs to be filtered
 * and queried by the type of resource affected.
 */
public enum EntityType {
    
    /**
     * A user account entity.
     * Used when auditing operations on user records such as creation,
     * modification, deletion, role changes, etc.
     */
    USER,
    
    /**
     * A group entity.
     * Used when auditing operations on group records such as creation,
     * modification, deletion, membership changes, etc.
     */
    GROUP,
    
    /**
     * An API entity.
     * Used when auditing operations on API configurations such as creation,
     * modification, deletion, visibility changes, etc.
     */
    API,
    
    /**
     * A group membership relationship entity.
     * Used when auditing operations that link users to groups,
     * such as adding or removing members.
     */
    GROUP_MEMBERSHIP,
    
    /**
     * An API-group link relationship entity.
     * Used when auditing operations that link APIs to groups,
     * such as assigning or removing API access.
     */
    API_GROUP_LINK
}
