package es.alesqui.intelligence.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Helper test that prints a BCrypt hash for a provided password.
 * Usage (PowerShell):
 *   mvn -q -Dtest=es.alesqui.intelligence.security.BCryptPrintTest -Dpwd="your-new-password" test
 * The generated hash will appear in the test output. Copy it and update your DB.
 */
public class BCryptPrintTest {

    @Test
    @DisplayName("Print BCrypt hash for given password")
    void printHash() {
        // Read the password from -Dpwd system property; default is "password123" if not provided
        String raw = System.getProperty("pwd", "password123");
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(raw);
        System.out.println("\n================= BCrypt Hash Generator =================");
        System.out.println("Raw password: " + raw);
        System.out.println("BCrypt hash : " + hash);
        System.out.println("========================================================\n");
        // No assertions; this test always passes. It's just a helper to print the hash.
    }
}
