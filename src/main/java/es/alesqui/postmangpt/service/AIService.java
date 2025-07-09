package es.alesqui.postmangpt.service;

import java.util.List;

import es.alesqui.postmangpt.dto.APIRequest;
import es.alesqui.postmangpt.dto.FormattedResponse;
import es.alesqui.postmangpt.dto.QueryValidation;
import es.alesqui.postmangpt.dto.rawapi.APIResponse;

/**
 * AI Service for PostmanGPT - Converts natural language to API calls
 * Core responsibility: Transform user queries into executable API requests
 */
public interface AIService {
  
  /**
   * Converts natural language query into executable API request
   * 
   * @param query User's natural language query (e.g., "Show me user with email john@example.com")
   * @param collectionId Which API collection to query against
   * @return Structured API request ready for execution
   */
  APIRequest convertQueryToAPIRequest(String query, String collectionId);
  
  /**
   * Formats API response data into user-friendly format
   * 
   * @param apiResponse Raw API response data
   * @param originalQuery Original user query for context
   * @return Human-readable formatted response
   */
  FormattedResponse formatAPIResponse(APIResponse apiResponse, String originalQuery);
  
  /**
   * Suggests possible queries based on available endpoints
   * 
   * @param collectionId API collection to analyze
   * @return List of example queries user can ask
   */
  List<String> suggestPossibleQueries(String collectionId);
  
  /**
   * Validates if a query can be executed against the collection
   * 
   * @param query User's natural language query
   * @param collectionId Target API collection
   * @return Validation result with details
   */
  QueryValidation validateQuery(String query, String collectionId);
}