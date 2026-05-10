package com.my.jwt.service;

import com.my.jwt.repository.UserRepository; // Loads User entities from the database
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.security.core.userdetails.UserDetails; // Spring Security user contract
import org.springframework.security.core.userdetails.UserDetailsService; // Interface implemented here
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Thrown when user not found
import org.springframework.stereotype.Service; // Marks as a Spring service bean
import org.springframework.transaction.annotation.Transactional; // Ensures the DB session is open during the load

/**
 * Spring Security {@link UserDetailsService} implementation that loads a
 * {@link com.my.jwt.entity.User} from MySQL by email address.
 *
 * <p>Spring Security calls this during authentication (login) and also from
 * {@link com.my.jwt.security.JwtAuthenticationFilter} on every request that
 * carries a JWT.</p>
 */
@Service // Registers this class as a Spring service bean
@RequiredArgsConstructor // Lombok: generates constructor that injects UserRepository
public class UserDetailsServiceImpl implements UserDetailsService { // Contract required by Spring Security

    /** Repository used to load the User entity by email. */
    private final UserRepository userRepository; // Injected by Spring

    /**
     * Loads a user by their email address (used as the Spring Security username).
     *
     * @param username the email address extracted from the JWT {@code sub} claim or login form
     * @return the {@link com.my.jwt.entity.User} which also implements {@link UserDetails}
     * @throws UsernameNotFoundException if no user with this email exists in the database
     */
    @Override
    @Transactional(readOnly = true) // Read-only transaction; no writes needed here
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Look up by email; throw the standard Spring Security exception if not found
        return userRepository.findByEmail(username) // SELECT * FROM users WHERE email = ?
                .orElseThrow(() -> new UsernameNotFoundException( // Throw if no row found
                        "User not found with email: " + username)); // Error message for the filter chain
    }
}
