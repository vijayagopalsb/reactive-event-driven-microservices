package com.reactiveevent.platform.auth.application;

import com.reactiveevent.platform.auth.application.events.LoginFailedEvent;
import com.reactiveevent.platform.auth.application.error.InvalidCredentialsException;
import com.reactiveevent.platform.auth.application.ports.*;
import com.reactiveevent.platform.auth.domain.repository.PermissionRepository;
import com.reactiveevent.platform.auth.domain.repository.RoleRepository;
import com.reactiveevent.platform.auth.domain.repository.UserProviderRepository;
import com.reactiveevent.platform.common.api.auth.LoginCommand;
import com.reactiveevent.platform.common.api.auth.LoginResult;
import com.reactiveevent.platform.common.application.auth.LoginUseCase;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.permission.Permission;
import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import com.reactiveevent.platform.common.domain.user.events.UserLoggedInEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * LoginUseCaseImpl — handles LOCAL email + password login.
 * <p>
 * This use case is called when the client sends:
 * POST /auth/login { email, password, provider: "LOCAL" }
 * <p>
 * The reactive chain (read top to bottom):
 * <p>
 * Step 1: Guard — reject non-LOCAL providers immediately
 * (GOOGLE/GITHUB go through OAuthLoginUseCase, not this one)
 * <p>
 * Step 2: Find user by email
 * → Mono.empty() if not found → "Invalid credentials"
 * (we never say "user not found" — attacker would learn which emails exist)
 * <p>
 * Step 3: Check user status
 * → INACTIVE or BLOCKED → reject with meaningful error
 * <p>
 * Step 4: Load the LOCAL UserProvider (has the password hash)
 * → Mono.empty() if user has no LOCAL provider
 * (e.g. registered via Google — has no password in our system)
 * <p>
 * Step 5: Verify raw password against BCrypt hash
 * → mismatch → "Invalid credentials"
 * <p>
 * Step 6: Load roles and permissions in parallel (Mono.zip)
 * → both DB calls run at the same time — more efficient than sequential
 * <p>
 * Step 7: Generate JWT access token (with roles + permissions + provider claims)
 * + refresh token (minimal claims, long-lived)
 * <p>
 * Step 8: Return LoginResult { accessToken, refreshToken }
 * <p>
 * Why @RequiredArgsConstructor?
 * Lombok generates a constructor with all final fields as parameters.
 * Spring sees that constructor and injects the beans automatically.
 * No @Autowired needed, no manual constructor needed.
 */

@Service
@RequiredArgsConstructor
public class LoginUseCaseImpl implements LoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUseCaseImpl.class);

    private final UserFinder userFinder;
    private final LoginEventPublisher loginEventPublisher;
    private final LoginFailureEventPublisher loginFailureEventPublisher;
    private final UserProviderRepository userProviderRepository;
    private final PasswordVerifier passwordVerifier;
    private final TokenGenerator tokenGenerator;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    public Mono<LoginResult> login(LoginCommand command) {

        // ── Step 1: Provider guard ────────────────────────────────────────────
        // This use case only handles LOCAL. OAuth2 has its own use case.
        if (command.provider() != AuthProvider.LOCAL) {
            log.warn("LoginUseCase called with non-LOCAL provider: {}", command.provider());
            return Mono.error(new IllegalArgumentException(
                    "Use /auth/oauth2/" + command.provider().name().toLowerCase()
                            + "/callback for " + command.provider() + " login"
            ));
        }

        // ── Step 2: Find user by email ────────────────────────────────────────
        // Step 2: Find user by email
        Mono<LoginResult> login = userFinder.findByEmail(command.email())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Login failed: email not found [{}]", command.email());
                    return Mono.error(new InvalidCredentialsException());
                }))
                // ── Step 3: Check user status ─────────────────────────────────
                .flatMap(user -> {
                    if (!user.isActive()) {
                        log.warn("Login failed: account not active for userId={}, status={}",
                                user.getId(), user.getStatus());
                        return Mono.error(new IllegalStateException(
                                "Account is " + user.getStatus().name().toLowerCase()
                        ));
                    }
                    return Mono.just(user);
                })

                // ── Step 4: Load LOCAL UserProvider ──────────────────────────
                // flatMap takes the User and returns a Mono<Pair(User, UserProvider)>
                // We need to carry both forward — so we zip them into a Tuple
                .flatMap(user ->
                        userProviderRepository
                                .findByEmailAndProvider(command.email(), AuthProvider.LOCAL)
                                .switchIfEmpty(Mono.defer(() -> {
                                    log.warn("Login failed: no LOCAL provider for email [{}]", command.email());
                                    // Don't reveal that the user exists but has no password
                                    return Mono.error(new InvalidCredentialsException());
                                }))
                                // zip(A, B) → Mono<Tuple2<A, B>>
                                // Lets us carry BOTH user and userProvider to the next step
                                .zipWith(Mono.just(user))
                )

                // ── Step 5: Verify password ───────────────────────────────────
                // tuple.getT1() = UserProvider, tuple.getT2() = User
                .flatMap(tuple -> {
                    UserProvider userProvider = tuple.getT1();
                    User user = tuple.getT2();

                    return passwordVerifier.verify(userProvider, command.password())
                            // After verification, we only need the User going forward
                            // (UserProvider's job is done — we have the user confirmed)
                            .map(verifiedProvider -> user);
                })

                // ── Steps 6 + 7: Load roles + permissions, then generate JWT ──
                .flatMap(user -> {

                    // Run BOTH DB calls in parallel using Mono.zip
                    // Mono.zip waits for ALL of them to complete, then combines results
                    // Sequential would be: load roles (wait) → load permissions (wait) = 2 round trips
                    // Parallel: both start at the same time = 1 round trip time
                    Mono<List<Role>> rolesMono = roleRepository
                            .findRolesByUserId(user.getId())
                            .collectList();

                    Mono<List<Permission>> permsMono = permissionRepository
                            .findPermissionsByUserId(user.getId())
                            .collectList();

                    return Mono.zip(rolesMono, permsMono)
                            .flatMap(tuple -> {
                                List<Role> roles = tuple.getT1();
                                List<Permission> permissions = tuple.getT2();

                                // ── Step 7: Generate tokens ───────────────────
                                LoginResult result = new LoginResult(
                                        tokenGenerator.generateAccessToken(
                                                user,
                                                AuthProvider.LOCAL,
                                                roles,
                                                permissions
                                        ),
                                        tokenGenerator.generateRefreshToken(user)
                                );

                                UserLoggedInEvent event = new UserLoggedInEvent(
                                        user.getId(),
                                        user.getEmail(),
                                        AuthProvider.LOCAL
                                );

                                // Kafka is an audit side effect: a broker outage must not
                                // turn valid credentials into a failed login. Failures are
                                // logged explicitly; durable delivery can be added with an outbox.
                                return loginEventPublisher.publish(event)
                                        .onErrorResume(error -> {
                                            log.error(
                                                    "Could not publish UserLoggedIn event for userId={}; allowing login",
                                                    user.getId(),
                                                    error
                                            );
                                            return Mono.empty();
                                        })
                                        .thenReturn(result);
                            });
                });

        return login.onErrorResume(InvalidCredentialsException.class, error ->
                loginFailureEventPublisher
                        .publish(LoginFailedEvent.invalidLocalCredentials())
                        .onErrorResume(publishError -> {
                            log.error("Could not publish failed-login audit event", publishError);
                            return Mono.empty();
                        })
                        .then(Mono.<LoginResult>error(error))
        );
    }
}
