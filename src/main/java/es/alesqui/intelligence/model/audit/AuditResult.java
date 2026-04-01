package es.alesqui.intelligence.model.audit;

/**
 * Enumeration of possible results for an audited operation.
 * 
 * This simple enum distinguishes between operations that completed
 * successfully and those that failed due to errors. It enables
 * quick filtering of audit logs to identify problematic operations
 * or security incidents.
 */
public enum AuditResult {
    
    /**
     * The operation completed successfully without errors.
     * The intended action was performed and changes were persisted.
     */
    SUCCESS,
    
    /**
     * The operation failed due to an error.
     * This could be due to validation errors, permission issues,
     * database constraints, or unexpected exceptions.
     * Additional details should be available in the errorMessage field.
     */
    FAILURE
}
