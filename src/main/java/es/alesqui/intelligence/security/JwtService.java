package es.alesqui.intelligence.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.core.User;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * Service for handling JSON Web Tokens (JWTs).
 *
 * This class encapsulates all logic for generating, validating, and extracting
 * information from JWTs. It uses a secret key and expiration settings defined
 * in the application's properties file.
 */
@Service
public class JwtService {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String LINK_GOOGLE_TYPE = "link-google";

    /**
     * The secret key used for signing and verifying JWTs.
     * Injected from the 'app.jwt.secret' property.
     * This should be a Base64 encoded string for security.
     */
    @Value("${app.jwt.secret}")
    private String secretKey;

    /**
     * The expiration time for standard access tokens, in milliseconds.
     * Injected from the 'app.jwt.expiration' property.
     */
    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    /**
     * The expiration time for refresh tokens, in milliseconds.
     * Injected from the 'app.jwt.refresh-expiration' property.
     */
    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Extracts the username (subject) from a given JWT token.
     *
     * @param token The JWT token string.
     * @return The username contained within the token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from a JWT token using a provided function.
     * This is a generic utility method to get any piece of information from the token payload.
     *
     * @param token          The JWT token string.
     * @param claimsResolver A function that specifies how to extract the desired claim.
     * @param <T>            The type of the claim to be returned.
     * @return The extracted claim.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a standard access JWT token for a user.
     * Uses the 'app.jwt.expiration' property for the expiration time.
     *
     * @param userDetails The user to generate the token for.
     * @return A signed JWT access token.
     */
    public String generateToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, jwtExpiration, ACCESS_TOKEN_TYPE);
    }

    /**
     * Generates a refresh JWT token for a user.
     * A refresh token typically has a longer lifespan and is used to obtain a new
     * access token without requiring the user to log in again.
     * Uses the 'app.jwt.refresh-expiration' property for the expiration time.
     *
     * @param userDetails The user to generate the token for.
     * @return A signed JWT refresh token.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration, REFRESH_TOKEN_TYPE);
    }

    /**
     * Validates a JWT token.
     * The validation checks two things:
     * 1. If the username in the token matches the username in the UserDetails object.
     * 2. If the token has not expired.
     *
     * @param token       The JWT token to validate.
     * @param userDetails The user details to validate the token against.
     * @return true if the token is valid, otherwise false.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Extracts the token type claim ("access" or "refresh") from a JWT token.
     *
     * @param token The JWT token string.
     * @return The token type, or null if the claim is absent.
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("typ", String.class));
    }

    /**
     * Returns true if the token is an access token (typ = "access").
     *
     * @param token The JWT token string.
     * @return true if the token type is "access".
     */
    public boolean isAccessToken(String token) {
        return ACCESS_TOKEN_TYPE.equals(extractTokenType(token));
    }

    /**
     * Returns true if the token is a refresh token (typ = "refresh").
     *
     * @param token The JWT token string.
     * @return true if the token type is "refresh".
     */
    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(extractTokenType(token));
    }

    /**
     * The core private method to build a token with specific claims and expiration.
     *
     * @param extraClaims Additional claims to include in the token payload.
     * @param userDetails The user for whom the token is being created.
     * @param expiration  The expiration time in milliseconds.
     * @param tokenType   The token type claim value ("access" or "refresh").
     * @return The compacted and signed JWT string.
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration,
            String tokenType
    ) {

    	List<String> authorities = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    	extraClaims.put("authorities", authorities);
    	extraClaims.put("typ", tokenType);

    	if (userDetails instanceof User u && u.getAuthProvider() != null) {
    	    extraClaims.put("authProvider", u.getAuthProvider().name());
    	} else {
    	    extraClaims.put("authProvider", "LOCAL");
    	}

        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Checks if a JWT token has expired.
     *
     * @param token The JWT token string.
     * @return true if the token's expiration date is before the current date, otherwise false.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from a JWT token.
     *
     * @param token The JWT token string.
     * @return The expiration date from the token.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses the JWT token and extracts all its claims.
     *
     * @param token The JWT token string.
     * @return The Claims object containing all data from the token's payload.
     */
    private Claims extractAllClaims(String token) {
    	    	
        return Jwts
        		.parser()
        		.verifyWith(getSignInKey())
        		.build()
        		.parseSignedClaims(token)
        		.getPayload();
    }

    /**
     * Generates a short-lived JWT used as a link challenge when a LOCAL account
     * needs to be linked to a Google identity.
     *
     * @param email     the user's email (becomes the JWT subject)
     * @param googleSub Google's stable user ID ('sub' claim)
     * @param ttl       time-to-live for the challenge token
     * @return a signed JWT challenge token
     */
    public String generateLinkChallenge(String email, String googleSub, Duration ttl) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", LINK_GOOGLE_TYPE);
        claims.put("googleSub", googleSub);
        return Jwts.builder()
                .claims(claims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Validates a link challenge JWT produced by {@link #generateLinkChallenge}.
     *
     * @param token     the challenge token to validate
     * @param email     the expected email (subject)
     * @param googleSub the expected Google 'sub' value
     * @return true if the token is valid, not expired, and matches the expected claims
     */
    public boolean isValidLinkChallenge(String token, String email, String googleSub) {
        try {
            Claims c = Jwts.parser().verifyWith(getSignInKey()).build()
                           .parseSignedClaims(token).getPayload();
            return LINK_GOOGLE_TYPE.equals(c.get("typ", String.class))
                && email.equals(c.getSubject())
                && googleSub.equals(c.get("googleSub", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Generates the signing key from the Base64 encoded secret.
     * This key is used to sign and verify JWTs with the HMAC-SHA algorithm.
     *
     * @return The cryptographic Key object for signing.
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
