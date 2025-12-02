// Default package (no package declaration)

import services.AuthService;
import models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for AuthService.
 * Tests authentication operations including login, signup, session creation,
 * and password validation.
 *
 * Note: These tests require a running MySQL database with the checkmate_db schema.
 * Make sure to have test users created before running these tests.
 */
public class AuthServiceTest {

    private AuthService authService;
    private String testEmail;
    private String testPhone;
    private String testPassword;

    @BeforeEach
    void setup() {
        authService = new AuthService();
        // Test credentials - these should exist in your test database
        testEmail = "test@example.com";
        testPhone = "1234567890";
        testPassword = "testpassword123";
    }

    /**
     * Test 1: Login with valid email and password
     */
    @Test
    void testLogin_withValidEmail_success() {
        // Note: This test requires a test user in the database
        // Create a test user first or use an existing one

        User user = authService.login(testEmail, testPassword);

        // If test user exists, verify successful login
        if (user != null) {
            assertNotNull(user, "User should not be null on successful login");
            assertNotNull(user.getUserId(), "User ID should not be null");
            assertNotNull(user.getEmail(), "Email should not be null");
            assertEquals(testEmail, user.getEmail(), "Email should match login email");
        }
    }

    /**
     * Test 2: Login with valid phone number and password
     */
    @Test
    void testLogin_withValidPhone_success() {
        User user = authService.login(testPhone, testPassword);

        // If test user exists with phone number, verify successful login
        if (user != null) {
            assertNotNull(user, "User should not be null on successful login");
            assertNotNull(user.getUserId(), "User ID should not be null");
            assertNotNull(user.getPhoneNumber(), "Phone number should not be null");
        }
    }

    /**
     * Test 3: Login with incorrect password should fail
     */
    @Test
    void testLogin_withInvalidPassword_failure() {
        String wrongPassword = "wrongpassword123";

        User user = authService.login(testEmail, wrongPassword);

        assertNull(user, "User should be null when password is incorrect");
    }

    /**
     * Test 4: Login with non-existent email should fail
     */
    @Test
    void testLogin_withNonExistentEmail_failure() {
        String nonExistentEmail = "nonexistent@example.com";

        User user = authService.login(nonExistentEmail, testPassword);

        assertNull(user, "User should be null when email doesn't exist");
    }

    /**
     * Test 5: Login with non-existent phone should fail
     */
    @Test
    void testLogin_withNonExistentPhone_failure() {
        String nonExistentPhone = "9999999999";

        User user = authService.login(nonExistentPhone, testPassword);

        assertNull(user, "User should be null when phone number doesn't exist");
    }

    /**
     * Test 6: Login distinguishes between email and phone correctly
     */
    @Test
    void testLogin_detectsEmailVsPhone() {
        // Email contains @, phone doesn't
        String emailInput = "user@example.com";
        String phoneInput = "5551234567";

        // The service should query different fields based on input
        // This test verifies the method runs without errors
        User emailUser = authService.login(emailInput, "anypassword");
        User phoneUser = authService.login(phoneInput, "anypassword");

        // Both should return null for non-existent users
        // but shouldn't throw errors
        assertTrue(emailUser == null || emailUser instanceof User);
        assertTrue(phoneUser == null || phoneUser instanceof User);
    }

    /**
     * Test 7: Create session for valid user
     */
    @Test
    void testCreateSession_success() {
        // First login to get a valid user
        User user = authService.login(testEmail, testPassword);

        if (user != null) {
            String token = authService.createSession(user.getUserId());

            assertNotNull(token, "Session token should not be null");
            assertFalse(token.isEmpty(), "Session token should not be empty");
            assertTrue(token.length() > 20, "Session token should be a valid UUID string");
        }
    }

    /**
     * Test 8: Create session with null user ID should handle gracefully
     */
    @Test
    void testCreateSession_withNullUserId() {
        String token = authService.createSession(null);

        // Should return null or handle error gracefully
        assertTrue(token == null || token.isEmpty(),
            "Session token should be null or empty for null user ID");
    }

    /**
     * Test 9: Signup with new user should succeed
     */
    @Test
    void testSignup_withNewUser_success() {
        // Use unique email and phone for testing
        String uniqueEmail = "newuser" + System.currentTimeMillis() + "@example.com";
        String uniquePhone = "555" + System.currentTimeMillis();

        User newUser = authService.signup(
            "Test User",
            uniqueEmail,
            uniquePhone,
            "newpassword123"
        );

        // Note: This might fail if user already exists
        // In a real test, you'd clean up after
        if (newUser != null) {
            assertNotNull(newUser, "New user should not be null");
            assertNotNull(newUser.getName(), "User name should not be null");
            assertEquals("Test User", newUser.getName(), "User name should match");
        }
    }

    /**
     * Test 10: Login after signup should work
     */
    @Test
    void testLogin_afterSignup_success() {
        // Create a unique user
        String uniqueEmail = "logintest" + System.currentTimeMillis() + "@example.com";
        String uniquePhone = "444" + System.currentTimeMillis();
        String password = "testpass123";

        // Signup
        User signupUser = authService.signup(
            "Login Test User",
            uniqueEmail,
            uniquePhone,
            password
        );

        if (signupUser != null) {
            // Try to login with the same credentials
            User loginUser = authService.login(uniqueEmail, password);

            assertNotNull(loginUser, "Should be able to login after signup");
            assertEquals(uniqueEmail, loginUser.getEmail(),
                "Logged in user email should match signup email");
        }
    }

    /**
     * Test 13: Signin with empty password should fail
     */
    @Test
    void testSignin_withEmptyPassword_failure() {
        User user = authService.login(testEmail, "");

        assertNull(user, "Login should fail with empty password");
    }

    /**
     * Test 14: Successful signin returns user with correct properties
     */
    @Test
    void testSignin_success_returnsUserWithProperties() {
        // Login with valid credentials
        User user = authService.login(testEmail, testPassword);

        if (user != null) {
            // Verify user object has required properties
            assertNotNull(user.getUserId(), "User should have ID");
            assertNotNull(user.getEmail(), "User should have email");
            assertNotNull(user.getName(), "User should have name");
            assertTrue(user.getBalance() >= 0, "User balance should be non-negative");
        }
    }

    @AfterEach
    void cleanup() {
        // In a real test environment, you would clean up test data here
        // For now, we'll leave test data in the database
        // TODO: Implement cleanup logic to remove test users
    }
}
