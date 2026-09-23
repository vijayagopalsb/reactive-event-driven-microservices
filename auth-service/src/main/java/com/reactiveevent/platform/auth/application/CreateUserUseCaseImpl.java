package com.reactiveevent.platform.auth.application;

import com.reactiveevent.platform.auth.domain.repository.RoleRepository;
import com.reactiveevent.platform.auth.domain.repository.UserProviderRepository;
import com.reactiveevent.platform.auth.domain.repository.UserRoleRepository;
import com.reactiveevent.platform.auth.domain.repository.WriteUserRepository;
import com.reactiveevent.platform.common.api.user.CreateUserCommand;
import com.reactiveevent.platform.common.api.user.UserResponse;
import com.reactiveevent.platform.common.application.user.CreateUserUseCase;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * CreateUserUseCaseImpl — admin creates a new LOCAL user account.
 *
 * Called when: POST /users with a valid ADMIN JWT.
 *
 * The reactive chain:
 *
 *   Step 1: Check email uniqueness
 *           → if email already exists → DuplicateEmailException
 *           → we check by trying to find the user; if found, reject
 *
 *   Step 2: Hash the raw password with BCrypt
 *           → BCrypt is CPU-intensive by design (slows brute force)
 *           → we never store the raw password anywhere
 *
 *   Step 3: Create and save User aggregate
 *           → User.createNew(email) generates a new UUID
 *           → INSERT INTO users
 *
 *   Step 4: Create and save UserProvider aggregate (LOCAL)
 *           → UserProvider.createLocal(userId, hashedPassword)
 *           → INSERT INTO user_providers
 *
 *   Step 5: Look up role by name, then assign it
 *           → SELECT id FROM roles WHERE name = 'ADMIN' (or USER, MANAGER)
 *           → INSERT IGNORE INTO user_roles (user_id, role_id)
 *
 *   Step 6: Build and return UserResponse
 *           → { id, email, status, roles: ["ADMIN"] }
 *
 * @Transactional:
 *   Wraps steps 3, 4, and 5 in a single DB transaction.
 *   If ANY of those three inserts fails, ALL are rolled back.
 *   We never end up with a partial user (identity without credentials or role).
 *
 * Why is PasswordEncoder injected here instead of a port?
 *   Hashing is a pure transformation — no IO, no side effects.
 *   Creating a port/adapter pair for it would be over-engineering.
 *   Spring Security's PasswordEncoder is already an abstraction (interface).
 *   The use case depends on that interface, not on BCrypt directly — good enough.
 */
@Service
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateUserUseCaseImpl.class);

    private final WriteUserRepository    writeUserRepository;
    private final UserProviderRepository userProviderRepository;
    private final RoleRepository         roleRepository;
    private final UserRoleRepository     userRoleRepository;
    private final PasswordEncoder        passwordEncoder;

    @Override
    @Transactional  // all three DB writes succeed together or roll back together
    public Mono<UserResponse> createUser(CreateUserCommand command) {

        log.info("Creating new LOCAL user: {}", command.email());

        // ── Step 1: Check email uniqueness ────────────────────────────────────
        // We attempt to find a user_providers row with this email + LOCAL.
        // If found → email already taken → reject.
        // If empty → safe to proceed.
        //
        // Why check user_providers and not just users?
        //   A user could exist with a Google provider but no LOCAL provider.
        //   In that case, the admin is creating a LOCAL password entry for them.
        //   For now we treat any existing email as taken — both cases rejected.
        //   Account linking (adding LOCAL to existing OAuth user) is a future feature.
        return userProviderRepository
                .findByEmailAndProvider(command.email(), AuthProvider.LOCAL)
                .flatMap(existing -> {
                    // If we get here, a row was found — email already registered
                    log.warn("Create user failed: email already exists [{}]", command.email());
                    return Mono.<UserResponse>error(
                        new IllegalArgumentException(
                            "Email already registered: " + command.email()
                        )
                    );
                })
                // switchIfEmpty fires when findByEmailAndProvider returns Mono.empty()
                // = email is not taken = safe to proceed with creation
                .switchIfEmpty(Mono.defer(() -> doCreateUser(command)));
    }

    // ── Steps 2–6: The actual creation chain ─────────────────────────────────
    // Extracted to a separate method to keep the entry point readable.
    // Mono.defer() ensures this runs lazily — only when subscribed.
    private Mono<UserResponse> doCreateUser(CreateUserCommand command) {

        // ── Step 2: Hash the raw password ─────────────────────────────────────
        // BCrypt adds a random salt automatically — same password hashed twice
        // produces different hashes. This is correct and expected.
        // Cost factor 10 = ~100ms per hash on modern hardware (brute force deterrent).
        String hashedPassword = passwordEncoder.encode(command.rawPassword());

        // ── Step 3: Create User aggregate and save ────────────────────────────
        User newUser = User.createNew(command.email());

        return writeUserRepository.save(newUser)

                // ── Step 4: Create UserProvider and save ──────────────────────
                // flatMap receives the saved User (with confirmed ID)
                // We create the LOCAL provider linked to that user's ID
                .flatMap(savedUser -> {
                    UserProvider provider = UserProvider.createLocal(
                            savedUser.getId(),
                            hashedPassword
                    );
                    return userProviderRepository.save(provider)
                            // After saving provider, carry savedUser forward
                            // We need it for the role assignment step
                            .thenReturn(savedUser);
                })

                // ── Step 5: Look up role → assign it ─────────────────────────
                // flatMap receives savedUser
                // We need to find the Role entity by name to get its UUID
                // Then insert into user_roles
                .flatMap(savedUser ->
                    roleRepository.findByName(command.role().name())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Role not found: " + command.role().name()
                                + ". Ensure the roles table is seeded correctly."
                            )))
                            .flatMap(role ->
                                userRoleRepository
                                    .assignRole(savedUser.getId(), role.getId())
                                    // After assigning role, carry (savedUser, roleName) forward
                                    // thenReturn = "ignore the Void result, return this value instead"
                                    .thenReturn(new UserWithRole(savedUser, role.getName()))
                            )
                )

                // ── Step 6: Build and return UserResponse ─────────────────────
                .map(userWithRole -> {
                    log.info("Successfully created user: id={}, email={}, role={}",
                            userWithRole.user().getId(),
                            userWithRole.user().getEmail(),
                            userWithRole.roleName());

                    return new UserResponse(
                            userWithRole.user().getId().getValue(),
                            userWithRole.user().getEmail(),
                            userWithRole.user().getStatus(),
                            List.of(userWithRole.roleName())  // roles list with the assigned role
                    );
                });
    }

    // ── Private helper record ─────────────────────────────────────────────────
    // Java record used as a simple data carrier to pass (user + roleName) together
    // through the last step of the reactive chain.
    // This is cleaner than using a Tuple2 — the field names are meaningful.
    private record UserWithRole(User user, String roleName) {}
}
