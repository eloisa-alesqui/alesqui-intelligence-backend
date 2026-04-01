package es.alesqui.intelligence.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated response wrapper for audit log queries.
 * 
 * This DTO wraps a page of audit log results along with pagination metadata,
 * enabling efficient navigation through large result sets in the admin UI.
 * 
 * Follows standard pagination patterns with zero-based page numbers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedAuditLogResponse<T> {
    
    /**
     * The list of audit logs for the current page.
     */
    private List<T> content;
    
    /**
     * The current page number (zero-based).
     */
    private int page;
    
    /**
     * The number of items per page.
     */
    private int size;
    
    /**
     * The total number of audit logs matching the query criteria.
     */
    private long totalElements;
    
    /**
     * The total number of pages available.
     */
    private int totalPages;
    
    /**
     * Whether this is the first page.
     */
    private boolean first;
    
    /**
     * Whether this is the last page.
     */
    private boolean last;
    
    /**
     * Whether there is a next page.
     */
    private boolean hasNext;
    
    /**
     * Whether there is a previous page.
     */
    private boolean hasPrevious;
}
