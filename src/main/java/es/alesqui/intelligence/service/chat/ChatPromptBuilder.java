package es.alesqui.intelligence.service.chat;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Builds role-aware system prompts by prepending persona instructions
 * based on the authenticated user's roles (IT vs Business).
 */
@Component
public class ChatPromptBuilder {

	private static final String ROLE_IT_INSTRUCTION = """
			IMPORTANT: You are speaking to a technical user (IT role). Be precise and use technical terminology.
			You can refer to APIs by name, endpoints by their operationId, and parameters by their technical type (e.g., string, integer).
			""";

	private static final String ROLE_BUSINESS_INSTRUCTION = """
			IMPORTANT: You are speaking to a business user. Use simple, non-technical language.
			Avoid jargon like 'API', 'endpoint', 'parameter', or data types.
			Translate technical concepts into business actions (e.g., instead of 'parameter name', say 'you need to provide a name').
			""";

	/**
	 * Builds a role-aware system prompt by prepending a concise persona instruction
	 * derived from the user's authorities.
	 *
	 * @param basePrompt     The original system prompt.
	 * @param authentication The Spring Security authentication (may be null).
	 * @return The basePrompt optionally prefixed with a role-specific persona instruction.
	 */
	public String buildPromptWithRole(String basePrompt, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return basePrompt;
		}

		Set<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());

		String personaInstruction = "";
		if (roles.contains("ROLE_IT")) {
			personaInstruction = ROLE_IT_INSTRUCTION;
		} else if (roles.contains("ROLE_BUSINESS")) {
			personaInstruction = ROLE_BUSINESS_INSTRUCTION;
		}

		if (personaInstruction.isEmpty()) {
			return basePrompt;
		}

		return personaInstruction + "\n\n---\n\n" + basePrompt;
	}
}
