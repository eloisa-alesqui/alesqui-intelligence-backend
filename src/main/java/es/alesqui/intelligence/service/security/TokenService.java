package es.alesqui.intelligence.service.security;

import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for generating secure tokens and calculating expiration times.
 * Used for account activation, password reset, and other security-related tokens.
 * 
 * This service provides cryptographically secure token generation using SecureRandom
 * and standardized expiration calculation logic.
 */
@Service
@Slf4j
public class TokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32; // bytes

    /**
     * Generates a cryptographically secure random token.
     * Uses SecureRandom to generate 32 random bytes converted to hexadecimal.
     * 
     * @return a 64-character hexadecimal string (32 bytes)
     */
    public String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = bytesToHex(tokenBytes);
        
        log.debug("Generated secure token with length: {} characters", token.length());
        return token;
    }

    /**
     * Calculates token expiration time from now.
     * 
     * @param hours number of hours until expiration
     * @return the expiration timestamp
     */
    public Instant calculateExpiration(long hours) {
        Instant expiration = Instant.now().plusSeconds(hours * 3600);
        log.debug("Calculated token expiration: {} ({} hours from now)", expiration, hours);
        return expiration;
    }

    /**
     * Calculates token expiration time for account activation (48 hours).
     * 
     * @return the expiration timestamp 48 hours from now
     */
    public Instant calculateActivationExpiration() {
        return calculateExpiration(48);
    }

    /**
     * Calculates token expiration time for password reset (1 hour).
     * 
     * @return the expiration timestamp 1 hour from now
     */
    public Instant calculatePasswordResetExpiration() {
        return calculateExpiration(1);
    }

    /**
     * Converts a byte array to a hexadecimal string.
     * 
     * @param bytes the byte array to convert
     * @return hexadecimal string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
