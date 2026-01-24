package es.alesqui.intelligence.validation;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PasswordValidator regex pattern.
 * Ensures password validation meets security requirements.
 */
class PasswordValidatorTest {

    private final Pattern passwordPattern = Pattern.compile(PasswordValidator.PASSWORD_PATTERN);

    @Test
    void testValidPasswords() {
        // Valid passwords - should PASS
        assertTrue(passwordPattern.matcher("SecurePass123!").matches(), "SecurePass123! should be valid");
        assertTrue(passwordPattern.matcher("MyP@ssw0rd").matches(), "MyP@ssw0rd should be valid");
        assertTrue(passwordPattern.matcher("Test1234@").matches(), "Test1234@ should be valid");
        assertTrue(passwordPattern.matcher("Abcd1234#").matches(), "Abcd1234# should be valid");
        assertTrue(passwordPattern.matcher("P@ssw0rd").matches(), "P@ssw0rd should be valid");
        assertTrue(passwordPattern.matcher("Admin123!").matches(), "Admin123! should be valid");
        assertTrue(passwordPattern.matcher("Qwerty123$").matches(), "Qwerty123$ should be valid");
    }

    @Test
    void testInvalidPasswords_TooShort() {
        // Too short - less than 8 characters
        assertFalse(passwordPattern.matcher("short1!").matches(), "short1! should be invalid (too short)");
        assertFalse(passwordPattern.matcher("Ab1!").matches(), "Ab1! should be invalid (too short)");
        assertFalse(passwordPattern.matcher("Test1@").matches(), "Test1@ should be invalid (too short)");
    }

    @Test
    void testInvalidPasswords_MissingUppercase() {
        // Missing uppercase letter
        assertFalse(passwordPattern.matcher("nouppercase1!").matches(), "nouppercase1! should be invalid (no uppercase)");
        assertFalse(passwordPattern.matcher("password123@").matches(), "password123@ should be invalid (no uppercase)");
        assertFalse(passwordPattern.matcher("test1234$").matches(), "test1234$ should be invalid (no uppercase)");
    }

    @Test
    void testInvalidPasswords_MissingLowercase() {
        // Missing lowercase letter
        assertFalse(passwordPattern.matcher("NOLOWERCASE1!").matches(), "NOLOWERCASE1! should be invalid (no lowercase)");
        assertFalse(passwordPattern.matcher("PASSWORD123@").matches(), "PASSWORD123@ should be invalid (no lowercase)");
        assertFalse(passwordPattern.matcher("TEST1234$").matches(), "TEST1234$ should be invalid (no lowercase)");
    }

    @Test
    void testInvalidPasswords_MissingNumber() {
        // Missing number
        assertFalse(passwordPattern.matcher("NoNumber!").matches(), "NoNumber! should be invalid (no number)");
        assertFalse(passwordPattern.matcher("Password@").matches(), "Password@ should be invalid (no number)");
        assertFalse(passwordPattern.matcher("TestTest$").matches(), "TestTest$ should be invalid (no number)");
    }

    @Test
    void testInvalidPasswords_MissingSpecialChar() {
        // Missing special character
        assertFalse(passwordPattern.matcher("NoSpecial1").matches(), "NoSpecial1 should be invalid (no special char)");
        assertFalse(passwordPattern.matcher("Password123").matches(), "Password123 should be invalid (no special char)");
        assertFalse(passwordPattern.matcher("TestTest1").matches(), "TestTest1 should be invalid (no special char)");
    }

    @Test
    void testEdgeCases() {
        // Edge cases with various special characters
        assertTrue(passwordPattern.matcher("Test123@Test").matches(), "Test123@Test should be valid");
        assertTrue(passwordPattern.matcher("Pass#word1").matches(), "Pass#word1 should be valid");
        assertTrue(passwordPattern.matcher("My$ecure1Pass").matches(), "My$ecure1Pass should be valid");
        assertTrue(passwordPattern.matcher("P@ssW0rd!").matches(), "P@ssW0rd! should be valid");
        
        // Exactly 8 characters with all requirements
        assertTrue(passwordPattern.matcher("Abcd123!").matches(), "Abcd123! should be valid (exactly 8 chars)");
        
        // Very long password
        assertTrue(passwordPattern.matcher("VeryLongP@ssw0rd12345!").matches(), "Very long password should be valid");
    }

    @Test
    void testSpecialCharacters() {
        // Test various special characters
        assertTrue(passwordPattern.matcher("Password1@").matches(), "@ should be valid special char");
        assertTrue(passwordPattern.matcher("Password1$").matches(), "$ should be valid special char");
        assertTrue(passwordPattern.matcher("Password1!").matches(), "! should be valid special char");
        assertTrue(passwordPattern.matcher("Password1%").matches(), "% should be valid special char");
        assertTrue(passwordPattern.matcher("Password1*").matches(), "* should be valid special char");
        assertTrue(passwordPattern.matcher("Password1?").matches(), "? should be valid special char");
        assertTrue(passwordPattern.matcher("Password1&").matches(), "& should be valid special char");
        assertTrue(passwordPattern.matcher("Password1#").matches(), "# should be valid special char");
    }
}
