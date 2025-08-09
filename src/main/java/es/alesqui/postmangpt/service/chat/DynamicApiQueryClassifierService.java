package es.alesqui.postmangpt.service.chat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import es.alesqui.postmangpt.dto.chat.response.ClassificationResponse;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.model.unified.UnifiedEndpoint;
import es.alesqui.postmangpt.service.UnifiedApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicApiQueryClassifierService {

	private final SpringAIService springAIService;
	private final UnifiedApiService unifiedApiService;
	private String cachedClassificationPrompt;
	private Instant lastPromptUpdate;

	/**
	 * Classify query based on available APIs
	 */
	public Mono<ClassificationResponse> classifyQuery(String query, String conversationId) {
	  Instant startTime = Instant.now();
	  
	  return getOrBuildClassificationPrompt(conversationId)
	      .flatMap(prompt -> classifyWithAI(query, prompt, conversationId))
	      .map(isDataQuery -> {
	          long processingTime = Duration.between(startTime, Instant.now()).toMillis();
	          
	          if (isDataQuery) {
	              return ClassificationResponse.dataQuery(
	                  "Query requires API data access", 
	                  0.8, 
	                  conversationId)
	                  .withProcessingTime(processingTime)
	                  .withMethod("AI_CLASSIFICATION");
	          } else {
	              return ClassificationResponse.directAnswer(
	                  "Query can be answered directly", 
	                  0.8, 
	                  conversationId)
	                  .withProcessingTime(processingTime)
	                  .withMethod("AI_CLASSIFICATION");
	          }
	      })
	      .doOnNext(response -> 
	          log.debug("Query '{}' classified as data query: {} with confidence: {}", 
	                   query, response.shouldUseReAct(), response.getConfidence()));
	}
	
	/**
	 * Classify query based on available APIs
	 */
	public Mono<Boolean> isDataQuery(String query, String conversationId) {
		return getOrBuildClassificationPrompt(conversationId)
				.flatMap(prompt -> classifyWithAI(query, prompt, conversationId))
				.doOnNext(isDataQuery -> log.debug("Query '{}' classified as data query: {}", query, isDataQuery));
	}

	/**
	 * Get cached prompt or build new one if APIs changed
	 */
	private Mono<String> getOrBuildClassificationPrompt(String conversationId) {
		// Cache prompt for 5 minutes to avoid rebuilding on every query
		if (cachedClassificationPrompt != null && lastPromptUpdate != null
				&& lastPromptUpdate.isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))) {
			return Mono.just(cachedClassificationPrompt);
		}

		return buildDynamicClassificationPrompt(conversationId).doOnNext(prompt -> {
			cachedClassificationPrompt = prompt;
			lastPromptUpdate = Instant.now();
			log.debug("Classification prompt updated");
		});
	}

	/**
	 * Build classification prompt based on available APIs
	 */
	private Mono<String> buildDynamicClassificationPrompt(String conversationId) {
		return unifiedApiService.findActiveApis().collectList()
				.flatMap(apis -> generatePromptFromApis(apis, conversationId));
	}

	private Mono<String> generatePromptFromApis(List<UnifiedApiDocument> apis, String conversationId) {
		StringBuilder prompt = new StringBuilder();

		prompt.append(
				"""
						You are a query classifier. Determine if a user query requires calling APIs to retrieve data.

						Answer with ONLY: YES or NO

						Answer YES if the query asks for information that would require calling any of these available data retrieval endpoints:

						""");

		// Add information about each API (only GET endpoints)
		for (UnifiedApiDocument api : apis) {
			prompt.append("📊 **").append(api.getName()).append("**\n");
			prompt.append("   Purpose: ").append(api.getDescription()).append("\n");

			List<String> getEndpoints = api.getEndpoints().stream()
					.filter(endpoint -> "GET".equalsIgnoreCase(endpoint.getMethod()))
					.map(endpoint -> "• " + endpoint.getOperationId()
							+ (endpoint.getSummary() != null ? " - " + endpoint.getSummary() : "")
							+ (endpoint.getDescription() != null ? " - " + endpoint.getDescription() : ""))
					.toList();

			if (!getEndpoints.isEmpty()) {
				prompt.append("   Available data endpoints:\n");
				getEndpoints.forEach(endpoint -> prompt.append("     ").append(endpoint).append("\n"));
			} else {
				prompt.append("   (No data retrieval endpoints available)\n");
			}
			prompt.append("\n");
		}

		// Generate examples using AI
		return generatePositiveExamplesFromGetEndpoints(apis, conversationId).map(aiGeneratedExamples -> {
			prompt.append("EXAMPLES of DATA QUERIES (answer YES):\n");
			prompt.append(aiGeneratedExamples);

			prompt.append("User query: \"{}\"");
			prompt.append("\n\nAnswer: ");

			return prompt.toString();
		});
	}

	/**
	 * Generate positive examples using AI based on available GET endpoints with
	 * their parameters
	 */
	private Mono<String> generatePositiveExamplesFromGetEndpoints(List<UnifiedApiDocument> apis, String conversationId) {
		if (apis.isEmpty()) {
			return Mono.just("- \"Show me data\"\n- \"Get information\"\n");
		}

		String apiContext = buildDetailedApiContextForExamples(apis);

		String exampleGenerationPrompt = """
				Based on the following APIs and their GET endpoints WITH PARAMETERS, generate 8-10 realistic user questions
				that would require calling these APIs to retrieve data.

				APIs available:
				%s

				IMPORTANT: Use the endpoint parameters to create specific, realistic questions that users would ask.
				For example:
				- If endpoint has {id} parameter: "Show me user details for ID X"
				- If endpoint has ?status= parameter: "Get all active users" or "Show me pending orders"
				- If endpoint has ?date= parameter: "What were sales on 2024-01-15?"
				- If endpoint has ?limit= parameter: "Show me the top 10 products"

				Format each question as: - "question text"

				Focus on:
				- Natural language questions that match the endpoint parameters
				- Questions that clearly need data from the APIs
				- Variety in question types (what, how many, show me, get, find, etc.)
				- Mix of general and specific questions using parameter context
				- Real-world scenarios users would encounter

				Examples format:
				- "What were the sales for customer ID X?"
				- "Show me orders from last week"
				- "Get product details for X"

				Generate the examples now:
				"""
				.formatted(apiContext);

		return springAIService.chat(
				"You are an expert at generating realistic user queries for API-based systems. Pay special attention to endpoint parameters to create contextual questions.",
				exampleGenerationPrompt, conversationId).map(response -> {
					String cleanedResponse = cleanAIGeneratedExamples(response);
					log.debug("Generated {} examples using AI with parameter context", countExamples(cleanedResponse));
					return cleanedResponse;
				}).onErrorReturn("""
						- "Show me the latest data"
						- "What information is available?"
						- "Get statistics for..."
						- "¿Qué datos tienes disponibles?"
						"""); // Fallback examples if AI fails
	}

	/**
	 * Build detailed API context string with parameters for example generation
	 */
	private String buildDetailedApiContextForExamples(List<UnifiedApiDocument> apis) {
		StringBuilder context = new StringBuilder();

		for (UnifiedApiDocument api : apis) {
			context.append("📊 ").append(api.getName()).append("\n");
			context.append("   Description: ").append(api.getDescription()).append("\n");

			// Only GET endpoints with detailed parameter information
			List<UnifiedEndpoint> getEndpoints = api.getEndpoints().stream()
					.filter(endpoint -> "GET".equalsIgnoreCase(endpoint.getMethod())).toList();

			if (!getEndpoints.isEmpty()) {
				context.append("   GET Endpoints:\n");
				getEndpoints.forEach(endpoint -> {
					context.append("     • ").append(endpoint.getOperationId());

					if (StringUtils.isNotBlank(endpoint.getSummary())) {
						context.append(" - ").append(endpoint.getSummary());
					}
					context.append("\n");

					if (StringUtils.isNotBlank(endpoint.getDescription())) {
						context.append(" - ").append(endpoint.getDescription());
					}
					context.append("\n");

					// Add parameter details
					if (endpoint.getParameters() != null && !endpoint.getParameters().isEmpty()) {
						context.append("       Parameters:\n");
						endpoint.getParameters().forEach(param -> {
							context.append("         - ").append(param.getName()).append(" (").append(param.getSchema().getType())
									.append(")");

							if (param.getDescription() != null) {
								context.append(": ").append(param.getDescription());
							}

							if (param.isRequired()) {
								context.append(" [REQUIRED]");
							}

							// Add example values if available
							if (param.getExample() != null) {
								context.append(" (e.g., ").append(param.getExample()).append(")");
							}

							context.append("\n");
						});
					} else {
						context.append("       No parameters\n");
					}
					context.append("\n");
				});
			}
			context.append("\n");
		}

		return context.toString();
	}

	/**
	 * Clean and format AI-generated examples
	 */
	private String cleanAIGeneratedExamples(String aiResponse) {
		return Arrays.stream(aiResponse.split("\n")).map(String::trim)
				.filter(line -> line.startsWith("-") || line.startsWith("•"))
				.map(line -> line.startsWith("•") ? line.replace("•", "-") : line).filter(line -> line.length() > 3) // Remove
																														// too
																														// short
																														// lines
				.limit(10) // Max 10 examples to keep prompt manageable
				.collect(Collectors.joining("\n")) + "\n";
	}

	/**
	 * Count examples for logging
	 */
	private int countExamples(String examples) {
		return (int) examples.lines().filter(line -> line.trim().startsWith("-")).count();
	}

	/**
	 * Classify with AI using the dynamic prompt
	 */
	private Mono<Boolean> classifyWithAI(String query, String promptTemplate, String conversationId) {
		String finalPrompt = promptTemplate.replace("{}", query);

		return springAIService.chat("You are a precise query classifier. Answer only YES or NO.", finalPrompt, conversationId)
				.map(this::parseResponse);
	}

	private boolean parseResponse(String response) {
		String cleanResponse = response.trim().toLowerCase();
		return cleanResponse.startsWith("yes") || cleanResponse.startsWith("sí");
	}

	

//	/**
//	 * Invalidate cache when APIs are updated
//	 */
//  TODO
//	@EventListener
//	public void onApiUpdated(ApiUpdatedEvent event) {
//		log.info("API configuration changed, invalidating classification prompt cache");
//		cachedClassificationPrompt = null;
//		lastPromptUpdate = null;
//	}
}
