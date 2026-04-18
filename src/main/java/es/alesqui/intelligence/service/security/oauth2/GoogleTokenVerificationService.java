package es.alesqui.intelligence.service.security.oauth2;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.config.GoogleOAuth2Properties;
import es.alesqui.intelligence.dto.security.oauth2.GoogleTokenInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Validates a Google id_token by calling Google's tokeninfo endpoint and
 * checking the claims inside the response.
 *
 * This is the first line of defense in the Google OAuth2 login flow. No user
 * lookup or account creation happens until this service confirms the token is
 * genuine and belongs to a verified Google account.
 *
 * Validation steps performed on every token:
 *   1. Calls Google's tokeninfo endpoint. Any 4xx/5xx response is treated as
 *      an invalid token.
 *   2. Verifies that the "aud" claim matches our configured client ID, ensuring
 *      the token was issued specifically for this application.
 *   3. Verifies that the "iss" claim is one of the accepted Google issuer URLs.
 *   4. Verifies that the token has not expired ("exp" claim).
 *   5. Verifies that the user's email address has been verified by Google
 *      ("email_verified" claim must be "true").
 *
 * Error responses:
 *   401 Unauthorized  - token is invalid, expired, or fails any claim check
 *   503 Service Unavailable - Google's tokeninfo endpoint could not be reached
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleTokenVerificationService {

    private final WebClient.Builder webClientBuilder;
    private final GoogleOAuth2Properties props;

    /**
     * Calls Google's tokeninfo endpoint with the provided id_token and validates
     * all security-relevant claims in the response.
     *
     * @param idToken the raw id_token string received from the frontend after
     *                the user completed the Google Sign-In popup
     * @return a Mono emitting the validated GoogleTokenInfo on success, or an
     *         error Mono with a ResponseStatusException on any validation failure
     */
    public Mono<GoogleTokenInfo> verify(String idToken) {
        return webClientBuilder.build().get()
                .uri(props.getTokeninfoUrl() + "?id_token={t}", idToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("unknown error")
                                .flatMap(body -> {
                                    log.warn("[GoogleOAuth2] tokeninfo rejected id_token: {}", body);
                                    return Mono.error(new ResponseStatusException(
                                            HttpStatus.UNAUTHORIZED, "Invalid Google id_token"));
                                }))
                .bodyToMono(GoogleTokenInfo.class)
                .flatMap(this::validate)
                .onErrorResume(
                        e -> !(e instanceof ResponseStatusException),
                        e -> {
                            log.error("[GoogleOAuth2] Error calling tokeninfo endpoint", e);
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE, "Google authentication service unavailable"));
                        });
    }

    /**
     * Checks the security-relevant claims of a GoogleTokenInfo that was
     * successfully deserialized from the tokeninfo response.
     *
     * Validation is intentionally strict: any missing or unexpected value
     * results in a 401 rather than a silent fallback, to avoid accepting
     * tokens that were not issued for this application.
     *
     * @param info the deserialized tokeninfo response
     * @return a Mono emitting the same info object if all checks pass, or an
     *         error Mono with 401 Unauthorized if any check fails
     */
    private Mono<GoogleTokenInfo> validate(GoogleTokenInfo info) {
        if (info.getAud() == null || !info.getAud().equals(props.getClientId())) {
            log.warn("[GoogleOAuth2] Invalid audience: {}", info.getAud());
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid audience"));
        }
        if (info.getIss() == null || !props.getAllowedIssuers().contains(info.getIss())) {
            log.warn("[GoogleOAuth2] Invalid issuer: {}", info.getIss());
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid issuer"));
        }
        if (info.getExp() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing expiration"));
        }
        long exp;
        try {
            exp = Long.parseLong(info.getExp());
        } catch (NumberFormatException e) {
            log.warn("[GoogleOAuth2] Invalid exp format: {}", info.getExp());
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token format"));
        }
        if (Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired"));
        }
        if (!"true".equalsIgnoreCase(info.getEmailVerified())) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email not verified"));
        }
        return Mono.just(info);
    }
}
