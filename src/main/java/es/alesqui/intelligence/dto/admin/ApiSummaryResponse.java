package es.alesqui.intelligence.dto.admin;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal projection of a unified API for access governance screens.
 *
 * Purpose:
 * - Render API lists within group detail without fetching large specifications.
 * - Drive assignment UIs by providing the minimal fields needed to identify and classify APIs.
 *
 * Notes:
 * - tags is a flat list of tag names to simplify client rendering.
 * - isPublic is true when the API has zero group links (visible to all users by policy).
 *
 * Example JSON fragment:
 * {"id":"api-123","name":"Billing API","description":"Handle invoicing","active":true,"version":"v1","tags":["billing","finance"],"isPublic":false}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSummaryResponse {
    /** Unique identifier of the unified API document. */
    private String id;

    /** Human readable API name. */
    private String name;

    /** Short description of the API purpose. */
    private String description;

    /** Whether this API is currently active. */
    private boolean active;

    /** Optional semantic version or label of the API. */
    private String version;

    /** List of tag names associated with the API. */
    private List<String> tags;

    /** True when the API is public because it has no group links. */
    private boolean isPublic;
}
