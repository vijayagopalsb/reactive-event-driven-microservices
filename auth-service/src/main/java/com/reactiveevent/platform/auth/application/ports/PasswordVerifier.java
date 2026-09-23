package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.user.UserProvider;
import reactor.core.publisher.Mono;

/**
 * PasswordVerifier — application port for BCrypt password verification.
 *
 * Why does this take UserProvider instead of User?
 *   The password hash lives in user_providers (not in users).
 *   A User has no password — only a LOCAL UserProvider does.
 *   Passing User here would require putting getPasswordHash() back on User,
 *   which we deliberately removed.
 *
 * Why return Mono<UserProvider> instead of Mono<Boolean>?
 *   Returning the UserProvider lets the caller continue the chain:
 *   verify(provider, password)
 *     .flatMap(verifiedProvider -> loadRoles(verifiedProvider.getUserId()))
 *     ...
 *   If we returned Mono<Boolean>, the caller would have to carry the UserProvider
 *   separately in the chain — more complex and error-prone.
 *
 * Contract:
 *   On success → returns the same UserProvider (verified)
 *   On failure → returns Mono.error(IllegalArgumentException("Invalid credentials"))
 *   Never reveal WHETHER the email exists or just the password is wrong.
 *   Always use the same error message: "Invalid credentials".
 *   This prevents user enumeration attacks (attacker probing which emails exist).
 */
public interface PasswordVerifier {
    Mono<UserProvider> verify(UserProvider userProvider, String rawPassword);
}
