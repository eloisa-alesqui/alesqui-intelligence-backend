package es.alesqui.postmangpt.service;

import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.exception.PromptGenerationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.Comparator;

/**
 * Service responsible for generating AI prompts based on endpoint information.
 * 
 * This service takes detailed endpoint information and creates structured
 * prompts that can be used with AI models to generate appropriate API requests
 * and analysis.
 */
@Slf4j
@Service
public class PromptGenerationService {

    // ================================================================================================
    // CONFIGURATION CONSTANTS
    // ================================================================================================

    private static final class Config {
        static final int MAX_QUERY_LENGTH = 1000;
        static final int MAX_PROMPT_LENGTH = 12000;
        static final int MIN_PROMPT_LENGTH = 50;
        static final int DESCRIPTION_MAX_LENGTH = 300;
        static final int MAX_ENDPOINTS_IN_PROMPT = 15;
        static final int REQUEST_BODY_MAX_LENGTH = 1000;
        static final int RESPONSE_BODY_MAX_LENGTH = 1000;
    }

    // ================================================================================================
    // PROMPT TYPES ENUM
    // ================================================================================================

    /**
     * Enumeration of available prompt types for different analysis purposes.
     */
    public enum PromptType {
        ANALYSIS("analysis"),
        TESTING("testing"),
        DOCUMENTATION("documentation"),
        SECURITY_REVIEW("security"),
        PERFORMANCE_OPTIMIZATION("performance");

        private final String value;

        PromptType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // ================================================================================================
    // EMBEDDED TEMPLATES USING TEXT BLOCKS
    // ================================================================================================

    /**
     * Comprehensive analysis template for general API endpoint analysis.
     */
    private static final String ANALYSIS_TEMPLATE = """
            You are an API analysis expert with deep knowledge of REST API design patterns,
            security best practices, and performance optimization.

            ## User Query
            %s

            ## Available API Endpoints (%d endpoints)
            %s

            ## Context Information
            - Total endpoints analyzed: %d
            - Endpoints with examples: %d
            - Authentication required: %s

            ## Your Task
            Analyze the provided endpoints in the context of the user's query and provide:

            1. **🎯 Relevant Endpoints**: Which endpoints directly address the user's query
            2. **💡 Implementation Recommendations**: Specific code examples with realistic data
            3. **🔒 Security Considerations**: Authentication, authorization, and data protection
            4. **⚡ Performance Tips**: Caching, pagination, rate limiting suggestions
            5. **✅ Best Practices**: Industry standards and common patterns
            6. **🚨 Potential Issues**: Common pitfalls and how to avoid them

            ## Guidelines
            - Be specific and actionable in your recommendations
            - Include complete curl commands with realistic sample data
            - Consider edge cases and error handling scenarios
            - Focus on practical, real-world solutions
            - Prioritize security and performance
            - Replace template variables ({{variable}}) with appropriate sample values
            - Provide multiple examples when relevant

            Please provide a comprehensive analysis that helps the developer implement
            their requirements effectively and securely.
            """;

    /**
     * Template focused on API testing strategies and test case generation.
     */
    private static final String TESTING_TEMPLATE = """
            You are an API testing expert specializing in comprehensive test strategies.

            ## User Query
            %s

            ## Available API Endpoints
            %s

            ## Your Task
            Create a comprehensive testing strategy including:

            1. **🧪 Test Cases**: Unit, integration, and end-to-end tests
            2. **📊 Test Data**: Realistic test scenarios and edge cases
            3. **🔍 Validation**: Response validation and error handling tests
            4. **⚡ Performance Tests**: Load testing and stress testing approaches
            5. **🔒 Security Tests**: Authentication, authorization, and vulnerability tests

            Focus on practical, executable test examples with specific assertions.
            """;

    /**
     * Template for generating comprehensive API documentation.
     */
    private static final String DOCUMENTATION_TEMPLATE = """
            You are an API documentation specialist focused on creating clear, comprehensive documentation.

            ## User Query
            %s

            ## Available API Endpoints
            %s

            ## Your Task
            Generate professional API documentation including:

            1. **📖 Endpoint Documentation**: Clear descriptions and usage examples
            2. **🔧 Integration Guide**: Step-by-step implementation instructions
            3. **💼 Use Cases**: Real-world scenarios and business logic
            4. **⚠️ Error Handling**: Common errors and troubleshooting
            5. **🎯 Best Practices**: Recommended usage patterns

            Create documentation that is both developer-friendly and business-oriented.
            """;

    /**
     * Template for security-focused API analysis and vulnerability assessment.
     */
    private static final String SECURITY_TEMPLATE = """
            You are a cybersecurity expert specializing in API security assessments.

            ## User Query
            %s

            ## Available API Endpoints
            %s

            ## Your Task
            Conduct a thorough security review covering:

            1. **🔐 Authentication & Authorization**: Security mechanisms analysis
            2. **🛡️ Input Validation**: Data sanitization and validation checks
            3. **🚨 Vulnerability Assessment**: Common security risks (OWASP API Top 10)
            4. **🔒 Data Protection**: Encryption, PII handling, and privacy concerns
            5. **📊 Security Monitoring**: Logging, alerting, and incident response

            Provide specific, actionable security recommendations with implementation examples.
            """;

    /**
     * Template for performance optimization and scalability analysis.
     */
    private static final String PERFORMANCE_TEMPLATE = """
            You are a performance optimization expert specializing in API efficiency.

            ## User Query
            %s

            ## Available API Endpoints
            %s

            ## Your Task
            Analyze and optimize API performance focusing on:

            1. **⚡ Response Time Optimization**: Caching strategies and database optimization
            2. **📈 Scalability**: Load balancing and horizontal scaling approaches
            3. **🔄 Rate Limiting**: Traffic management and throttling strategies
            4. **💾 Resource Management**: Memory usage and connection pooling
            5. **📊 Monitoring**: Performance metrics and alerting strategies

            Provide specific optimization techniques with measurable performance improvements.
            """;

    /**
     * Fallback template used when no endpoints are available for analysis.
     */
    private static final String NO_ENDPOINTS_TEMPLATE = """
            You are an API expert. The user has submitted a query but no API endpoints
            are currently available for analysis.

            ## User Query
            %s

            ## Your Task
            Since no specific endpoints are available, please provide:

            1. **🎯 General Guidance**: Best practices related to the user's query
            2. **🏗️ Recommended Architecture**: What endpoints and structure would be ideal
            3. **💡 Implementation Suggestions**: Common patterns and approaches
            4. **🔒 Security Considerations**: Relevant security measures
            5. **📚 Resources**: Helpful documentation and tools

            Focus on providing valuable, actionable guidance even without specific endpoint information.
            """;

    // ================================================================================================
    // PUBLIC API METHODS
    // ================================================================================================

    /**
     * Generates a comprehensive analysis prompt for API endpoints based on user query.
     * This is the primary method for general API analysis.
     * 
     * @param userQuery The user's natural language query describing their needs
     * @param availableEndpoints List of available API endpoints to analyze
     * @return A formatted prompt ready for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generateAnalysisPrompt(String userQuery, List<EndpointInfo> availableEndpoints) {
        return generatePrompt(userQuery, availableEndpoints, PromptType.ANALYSIS);
    }

    /**
     * Generates a testing-focused prompt for API endpoints.
     * Specializes in test case generation and testing strategies.
     * 
     * @param userQuery The user's query related to testing requirements
     * @param availableEndpoints List of endpoints to generate tests for
     * @return A testing-focused prompt for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generateTestingPrompt(String userQuery, List<EndpointInfo> availableEndpoints) {
        return generatePrompt(userQuery, availableEndpoints, PromptType.TESTING);
    }

    /**
     * Generates a documentation-focused prompt for API endpoints.
     * Specializes in creating comprehensive API documentation.
     * 
     * @param userQuery The user's query related to documentation needs
     * @param availableEndpoints List of endpoints to document
     * @return A documentation-focused prompt for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generateDocumentationPrompt(String userQuery, List<EndpointInfo> availableEndpoints) {
        return generatePrompt(userQuery, availableEndpoints, PromptType.DOCUMENTATION);
    }

    /**
     * Generates a security review prompt for API endpoints.
     * Specializes in security assessment and vulnerability analysis.
     * 
     * @param userQuery The user's query related to security concerns
     * @param availableEndpoints List of endpoints to security review
     * @return A security-focused prompt for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generateSecurityPrompt(String userQuery, List<EndpointInfo> availableEndpoints) {
        return generatePrompt(userQuery, availableEndpoints, PromptType.SECURITY_REVIEW);
    }

    /**
     * Generates a performance optimization prompt for API endpoints.
     * Specializes in performance analysis and optimization recommendations.
     * 
     * @param userQuery The user's query related to performance optimization
     * @param availableEndpoints List of endpoints to optimize
     * @return A performance-focused prompt for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generatePerformancePrompt(String userQuery, List<EndpointInfo> availableEndpoints) {
        return generatePrompt(userQuery, availableEndpoints, PromptType.PERFORMANCE_OPTIMIZATION);
    }

    /**
     * Main prompt generation method with type selection.
     * Provides flexibility to generate different types of prompts based on the specified type.
     * 
     * @param userQuery The user's natural language query
     * @param availableEndpoints List of available API endpoints to analyze
     * @param promptType The type of prompt to generate (analysis, testing, documentation, etc.)
     * @return A formatted prompt ready for AI analysis
     * @throws IllegalArgumentException if inputs are invalid
     * @throws PromptGenerationException if prompt generation fails
     */
    public String generatePrompt(String userQuery, List<EndpointInfo> availableEndpoints, PromptType promptType) {
        log.debug("Generating {} prompt for query: '{}' with {} endpoints", 
                 promptType.getValue(), userQuery,
                 availableEndpoints != null ? availableEndpoints.size() : 0);

        // Validate inputs
        validatePromptInputs(userQuery, availableEndpoints);

        // Generate appropriate prompt based on available endpoints and type
        String prompt = (availableEndpoints == null || availableEndpoints.isEmpty())
                ? generateNoEndpointsPrompt(userQuery)
                : generateTypedPrompt(userQuery, availableEndpoints, promptType);

        // Validate final output
        validateGeneratedPrompt(prompt);

        log.debug("Generated {} prompt successfully. Length: {} characters", 
                 promptType.getValue(), prompt.length());
        return prompt;
    }

    // ================================================================================================
    // PRIVATE PROMPT GENERATION METHODS
    // ================================================================================================

    /**
     * Generates typed prompt based on the specified prompt type.
     * Handles sorting of endpoints by relevance and delegates to appropriate template.
     * 
     * @param userQuery The user's query
     * @param availableEndpoints List of endpoints to include in the prompt
     * @param promptType The type of prompt to generate
     * @return Formatted prompt string for the specified type
     */
    private String generateTypedPrompt(String userQuery, List<EndpointInfo> availableEndpoints, PromptType promptType) {
        // Sort endpoints by relevance score if available
        List<EndpointInfo> sortedEndpoints = availableEndpoints.stream()
                .sorted(Comparator.comparing(EndpointInfo::getMatchScore, 
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Config.MAX_ENDPOINTS_IN_PROMPT)
                .collect(Collectors.toList());

        String formattedEndpoints = formatEndpointsForTemplate(sortedEndpoints);
        String escapedQuery = escapeForTemplate(userQuery);

        return switch (promptType) {
            case ANALYSIS -> generateAnalysisPromptInternal(escapedQuery, formattedEndpoints, sortedEndpoints);
            case TESTING -> TESTING_TEMPLATE.formatted(escapedQuery, formattedEndpoints);
            case DOCUMENTATION -> DOCUMENTATION_TEMPLATE.formatted(escapedQuery, formattedEndpoints);
            case SECURITY_REVIEW -> SECURITY_TEMPLATE.formatted(escapedQuery, formattedEndpoints);
            case PERFORMANCE_OPTIMIZATION -> PERFORMANCE_TEMPLATE.formatted(escapedQuery, formattedEndpoints);
        };
    }

    /**
     * Generates analysis prompt with additional context information.
     * Calculates statistics about the endpoints to provide better context to the AI.
     * 
     * @param escapedQuery The user query with special characters escaped
     * @param formattedEndpoints Formatted string representation of endpoints
     * @param endpoints List of endpoints for statistical analysis
     * @return Formatted analysis prompt with context information
     */
    private String generateAnalysisPromptInternal(String escapedQuery, String formattedEndpoints, 
                                                 List<EndpointInfo> endpoints) {
        int totalEndpoints = endpoints.size();
        int endpointsWithExamples = (int) endpoints.stream()
                .filter(e -> StringUtils.isNotBlank(e.getRequestBodyExample()) || 
                            StringUtils.isNotBlank(e.getResponseExample()))
                .count();
        
        String authRequired = endpoints.stream()
                .anyMatch(e -> e.getHeaders() != null && 
                         e.getHeaders().keySet().stream()
                         .anyMatch(h -> h.toLowerCase().contains("authorization"))) 
                ? "Yes" : "Unknown";

        return ANALYSIS_TEMPLATE.formatted(
                escapedQuery, 
                totalEndpoints,
                formattedEndpoints, 
                totalEndpoints, 
                endpointsWithExamples,
                authRequired
        );
    }

    /**
     * Generates fallback prompt when no endpoints are available.
     * Provides general guidance when specific endpoint information is not available.
     * 
     * @param userQuery The user's query
     * @return Formatted fallback prompt
     */
    private String generateNoEndpointsPrompt(String userQuery) {
        log.debug("No endpoints available, using fallback template");
        return NO_ENDPOINTS_TEMPLATE.formatted(escapeForTemplate(userQuery));
    }

    /**
     * Formats the list of endpoints into a readable template format.
     * Creates a structured representation of all endpoints with visual separators.
     * 
     * @param endpoints List of endpoints to format
     * @return Formatted string representation of all endpoints
     */
    private String formatEndpointsForTemplate(List<EndpointInfo> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return "No endpoints available.";
        }

        return endpoints.stream()
                .filter(endpoint -> StringUtils.isNotBlank(endpoint.getUrl()))
                .map(this::formatSingleEndpoint)
                .collect(Collectors.joining(
                        "\n" + "─".repeat(80) + "\n\n", // Visual delimiter between endpoints
                        "### 🚀 Endpoint Details\n\n", // Section header with emoji
                        "\n" // suffix
                ));
    }

    /**
     * Formats a single endpoint with all available information from EndpointInfo.
     * Creates a comprehensive, structured representation of an individual endpoint.
     * 
     * @param endpoint The endpoint to format
     * @return Formatted string representation of the endpoint
     */
    private String formatSingleEndpoint(EndpointInfo endpoint) {
        StringBuilder sb = new StringBuilder();

        // Main endpoint signature with method emoji and relevance score
        sb.append("#### ").append(getMethodEmoji(endpoint.getMethod()))
          .append(" **").append(endpoint.getMethod()).append("** `").append(endpoint.getUrl()).append("`");

        // Add relevance score prominently if available
        if (endpoint.getMatchScore() != null && endpoint.getMatchScore() > 0) {
            sb.append(" 🎯 *Relevance: ").append(String.format("%.1f%%", endpoint.getMatchScore() * 100)).append("*");
        }

        // Add name if available
        Optional.ofNullable(endpoint.getName())
                .filter(StringUtils::isNotBlank)
                .ifPresent(name -> sb.append("\n- **📝 Name**: ").append(escapeForTemplate(name)));

        // Add description with length limitation
        Optional.ofNullable(endpoint.getDescription())
                .filter(StringUtils::isNotBlank)
                .map(desc -> StringUtils.abbreviate(desc, Config.DESCRIPTION_MAX_LENGTH))
                .ifPresent(desc -> sb.append("\n- **📋 Description**: ").append(escapeForTemplate(desc)));

        // Add path parameters with code formatting
        if (endpoint.getPathParameters() != null && !endpoint.getPathParameters().isEmpty()) {
            String pathParams = endpoint.getPathParameters().stream()
                    .map(param -> "`" + param + "`")
                    .collect(Collectors.joining(", "));
            sb.append("\n- **🔗 Path Parameters**: ").append(pathParams);
        }

        // Add query parameters with structured formatting
        if (endpoint.getQueryParameters() != null && !endpoint.getQueryParameters().isEmpty()) {
            String queryParams = formatMapParameters(endpoint.getQueryParameters());
            sb.append("\n- **❓ Query Parameters**: ").append(queryParams);
        }

        // Add headers information
        if (endpoint.getHeaders() != null && !endpoint.getHeaders().isEmpty()) {
            String headers = formatMapParameters(endpoint.getHeaders());
            sb.append("\n- **📤 Headers**: ").append(headers);
        }

        // Add request body with length limitation and JSON formatting
        Optional.ofNullable(endpoint.getRequestBodyExample())
                .filter(StringUtils::isNotBlank)
                .map(body -> StringUtils.abbreviate(body, Config.REQUEST_BODY_MAX_LENGTH))
                .ifPresent(body -> sb.append("\n- **📥 Request Body Example**:\n```json\n")
                                    .append(body).append("\n```"));

        // Add response example with length limitation and JSON formatting
        Optional.ofNullable(endpoint.getResponseExample())
                .filter(StringUtils::isNotBlank)
                .map(response -> StringUtils.abbreviate(response, Config.RESPONSE_BODY_MAX_LENGTH))
                .ifPresent(response -> sb.append("\n- **📤 Response Example**:\n```json\n")
                                         .append(response).append("\n```"));

        // Add tags with code formatting
        if (endpoint.getTags() != null && !endpoint.getTags().isEmpty()) {
            String tags = endpoint.getTags().stream()
                    .map(tag -> "`" + tag + "`")
                    .collect(Collectors.joining(", "));
            sb.append("\n- **🏷️ Tags**: ").append(tags);
        }

        return sb.toString();
    }

    /**
     * Gets emoji representation for HTTP methods to improve visual identification.
     * Maps standard HTTP methods to appropriate emoji representations.
     * 
     * @param method The HTTP method (GET, POST, PUT, DELETE, etc.)
     * @return Emoji string representing the HTTP method
     */
    private String getMethodEmoji(String method) {
        if (method == null) return "🔵";
        
        return switch (method.toUpperCase()) {
            case "GET" -> "🟢";
            case "POST" -> "🟡";
            case "PUT" -> "🟠";
            case "DELETE" -> "🔴";
            case "PATCH" -> "🟣";
            case "HEAD" -> "⚪";
            case "OPTIONS" -> "⚫";
            default -> "🔵";
        };
    }

    /**
     * Formats a map of parameters (query params or headers) for display.
     * Creates a readable representation of key-value parameter pairs.
     * 
     * @param parameters Map of parameter name to description/value
     * @return Formatted string representation with code formatting
     */
    private String formatMapParameters(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> "`" + entry.getKey() + "`: " + 
                             (StringUtils.isNotBlank(entry.getValue()) ? entry.getValue() : "*(no description)*"))
                .collect(Collectors.joining(", "));
    }

    // ================================================================================================
    // VALIDATION AND UTILITY METHODS
    // ================================================================================================

    /**
     * Validates the input parameters before generating a prompt.
     * Ensures data integrity and prevents invalid prompt generation with detailed error messages.
     * 
     * @param userQuery The user's natural language query
     * @param availableEndpoints List of available API endpoints
     * @throws IllegalArgumentException if inputs are null/empty
     * @throws PromptGenerationException if query exceeds maximum length
     */
    private void validatePromptInputs(String userQuery, List<EndpointInfo> availableEndpoints) {
        if (StringUtils.isBlank(userQuery)) {
            throw new IllegalArgumentException("User query cannot be null or empty");
        }

        if (availableEndpoints == null) {
            throw new IllegalArgumentException("Available endpoints list cannot be null (empty list is acceptable)");
        }

        if (userQuery.length() > Config.MAX_QUERY_LENGTH) {
            throw new PromptGenerationException(
                    "Query length (%d characters) exceeds maximum allowed (%d characters). Please shorten your query."
                            .formatted(userQuery.length(), Config.MAX_QUERY_LENGTH));
        }

        // Validate endpoint data quality and log warnings for incomplete data
        if (!availableEndpoints.isEmpty()) {
            long invalidEndpoints = availableEndpoints.stream()
                    .filter(endpoint -> StringUtils.isBlank(endpoint.getUrl()) || 
                                      StringUtils.isBlank(endpoint.getMethod()))
                    .count();
            
            if (invalidEndpoints > 0) {
                log.warn("Found {} endpoints with missing URL or method information", invalidEndpoints);
            }
        }
    }

    /**
     * Validates the final generated prompt before returning it.
     * Ensures the prompt meets quality and length requirements with specific error messages.
     * 
     * @param prompt The generated prompt string
     * @throws PromptGenerationException if prompt is invalid, too long, or too short
     */
    private void validateGeneratedPrompt(String prompt) {
        if (StringUtils.isBlank(prompt)) {
            throw new PromptGenerationException("Generated prompt is empty or null");
        }

        if (prompt.length() > Config.MAX_PROMPT_LENGTH) {
            throw new PromptGenerationException(
                    "Generated prompt length (%d characters) exceeds maximum allowed (%d characters). " +
                    "Consider reducing the number of endpoints or simplifying the query."
                            .formatted(prompt.length(), Config.MAX_PROMPT_LENGTH));
        }

        if (prompt.length() < Config.MIN_PROMPT_LENGTH) {
            throw new PromptGenerationException(
                    "Generated prompt length (%d characters) is below minimum required (%d characters). " +
                    "The prompt may not contain sufficient information for effective analysis."
                            .formatted(prompt.length(), Config.MIN_PROMPT_LENGTH));
        }

        // Quality checks to ensure prompt structure is correct
        if (!prompt.contains("## User Query")) {
            log.warn("Generated prompt may be missing user query section");
        }
    }

    /**
     * Escapes special characters in text for safe template processing.
     * Prevents template injection and formatting issues by escaping potentially problematic characters.
     * 
     * @param text The text to escape
     * @return Escaped text safe for template use, empty string if input is null
     */
    private String escapeForTemplate(String text) {
        if (StringUtils.isBlank(text)) {
            return StringUtils.EMPTY;
        }

        // Escape characters that could interfere with template formatting
        return StringUtils.replaceEach(text, 
                new String[] { "\"", "\n", "\r", "\t", "\\", "`" },
                new String[] { "\\\"", "\\n", "\\r", "\\t", "\\\\", "\\`" });
    }
}