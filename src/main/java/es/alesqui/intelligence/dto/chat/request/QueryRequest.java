package es.alesqui.intelligence.dto.chat.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Simplified request DTO for basic query operations. Used when minimal
 * information is needed for processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

	/**
	 * User's natural language query or question.
	 */
	@NotBlank(message = "Query cannot be empty")
	@Size(max = 1000, message = "Query too long")
	private String query;

	/**
	 * Optional context or additional information for the query. Can contain
	 * previous conversation history or relevant details.
	 */
	@Size(max = 500, message = "Context too long")
	private String context;

	/**
	 * Creates a query request with just the query text.
	 *
	 * @param query user's question
	 * @return new QueryRequest instance
	 */
	public static QueryRequest of(String query) {
		return new QueryRequest(query, null);
	}

	/**
	 * Creates a query request with query and context.
	 *
	 * @param query   user's question
	 * @param context additional context information
	 * @return new QueryRequest instance
	 */
	public static QueryRequest withContext(String query, String context) {
		return new QueryRequest(query, context);
	}

	/**
	 * Checks if this request has context information.
	 *
	 * @return true if context is not null and not empty
	 */
	public boolean hasContext() {
		return context != null && !context.trim().isEmpty();
	}
}
