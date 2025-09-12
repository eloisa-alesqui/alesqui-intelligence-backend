package es.alesqui.intelligence.config.security;

import es.alesqui.intelligence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration class for application-level beans, primarily for security.
 *
 * This class provides the essential beans required by the Spring Security
 * configuration, such as the ReactiveUserDetailsService, PasswordEncoder,
 * and ReactiveAuthenticationManager.
 */
@Configuration
@RequiredArgsConstructor
public class AuthenticationConfig {

    private final UserRepository userRepository;

    /**
     * Provides a bean for the ReactiveUserDetailsService.
     *
     * This service is responsible for loading user-specific data in a non-blocking
     * way. It uses the ReactiveUserRepository to find a user by their username.
     * If the user is not found, it returns a Mono.error.
     *
     * @return An implementation of ReactiveUserDetailsService.
     */
    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
        		.cast(UserDetails.class)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException("User not found: " + username)));
    }

    /**
     * Provides a bean for the PasswordEncoder.
     *
     * This is used to encode and verify passwords. BCrypt is the standard,
     * secure choice for password hashing.
     *
     * @return A PasswordEncoder implementation.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Provides a bean for the ReactiveAuthenticationManager.
     *
     * This manager orchestrates the authentication process. It uses the
     * ReactiveUserDetailsService to fetch the user and the PasswordEncoder
     * to validate the provided credentials against the stored password hash.
     *
     * @param userDetailsService The service to load user data.
     * @param passwordEncoder The encoder to validate passwords.
     * @return A ReactiveAuthenticationManager implementation.
     */
    @Bean
    public ReactiveAuthenticationManager authenticationManager(ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var authenticationManager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }
}