package com.reactiveevent.platform.auth.infrastructure.security;

import com.reactiveevent.platform.auth.application.error.InvalidCredentialsException;
import com.reactiveevent.platform.auth.application.ports.PasswordVerifier;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * PasswordVerifierImpl — BCrypt password verification.
 *
 * This is the infrastructure implementation of the PasswordVerifier port.
 * It uses Spring Security's PasswordEncoder (BCrypt) to compare the raw
 * password from the login request against the stored hash.
 *
 * Why Mono.fromSupplier()?
 *   BCrypt is a CPU-intensive operation (that's by design — it makes brute force slow).
 *   Mono.fromSupplier() wraps the blocking BCrypt call in a Mono without
 *   blocking the reactive event loop thread.
 *   In production you'd further wrap this with .subscribeOn(Schedulers.boundedElastic())
 *   to move it off the event loop entirely — we'll add that in Phase 4.8.
 *
 * Security note:
 *   We never log the raw password or the hash — only the outcome.
 *   We use a generic "Invalid credentials" message whether the hash doesn't match
 *   OR the provider has no password (preventing callers from distinguishing the two cases).
 */
@Component
@RequiredArgsConstructor
public class PasswordVerifierImpl implements PasswordVerifier {

    private static final Logger log = LoggerFactory.getLogger(PasswordVerifierImpl.class);

    private final PasswordEncoder passwordEncoder;

    @Override
    public Mono<UserProvider> verify(UserProvider userProvider, String rawPassword) {
        return Mono.fromSupplier(() -> {

            // Guard: LOCAL provider must have a password hash
            // If somehow a non-LOCAL provider ended up here, fail fast
            if (!userProvider.hasPassword()) {
                log.warn("Password verification attempted on provider with no hash: {}",
                         userProvider.getProvider());
                throw new InvalidCredentialsException();
            }

            boolean matches = passwordEncoder.matches(rawPassword, userProvider.getPasswordHash());

            if (matches) {
                log.debug("Password verified for userId: {}", userProvider.getUserId());
                return userProvider;
            }

            // Do NOT log the email or anything that identifies the user here.
            // If this log line is compromised, we don't want to leak user data.
            log.warn("Password verification failed for userId: {}", userProvider.getUserId());
            throw new InvalidCredentialsException();
        });
    }
}
