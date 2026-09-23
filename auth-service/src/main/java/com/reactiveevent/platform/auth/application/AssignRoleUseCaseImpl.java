package com.reactiveevent.platform.auth.application;

import com.reactiveevent.platform.auth.domain.repository.RoleRepository;
import com.reactiveevent.platform.auth.domain.repository.UserRepository;
import com.reactiveevent.platform.auth.domain.repository.UserRoleRepository;
import com.reactiveevent.platform.common.api.user.AssignRoleCommand;
import com.reactiveevent.platform.common.application.user.AssignRoleUseCase;
import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * AssignRoleUseCaseImpl — admin assigns an existing role to an existing user.
 *
 * Called when: POST /users/{userId}/roles/{roleId} with a valid ADMIN JWT.
 *
 * The reactive chain:
 *
 *   Step 1: Validate user exists
 *           → findById(userId) → error if not found
 *           → gives a clean "User not found" message instead of a DB constraint error
 *
 *   Step 2: Validate role exists
 *           → findByName(roleId) → error if not found
 *           → Parallel with Step 1 using Mono.zip (both DB reads run at the same time)
 *
 *   Step 3: Assign the role
 *           → INSERT IGNORE INTO user_roles (user_id, role_id)
 *           → Idempotent: assigning same role twice is silently ignored
 *
 *   Step 4: Return Mono<Void>
 *           → HTTP 204 No Content — no body in the response
 *
 * Why no @Transactional?
 *   Only one write operation (step 3).
 *   If step 3 fails, nothing is left in an inconsistent state.
 *   No need for rollback — each step is independently safe.
 *
 * Why Mono.zip for steps 1 and 2?
 *   User lookup and Role lookup are independent — neither depends on the other.
 *   Running them in parallel cuts the response time roughly in half.
 *   Mono.zip waits for both, then proceeds only if both succeed.
 *   If either returns empty → the switchIfEmpty fires → meaningful error returned.
 */
@Service
@RequiredArgsConstructor
public class AssignRoleUseCaseImpl implements AssignRoleUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssignRoleUseCaseImpl.class);

    private final UserRepository     userRepository;
    private final RoleRepository     roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public Mono<Void> assignRole(AssignRoleCommand command) {

        log.info("Assigning role {} to user {}", command.roleId(), command.userId());

        // ── Steps 1 + 2: Validate user and role in PARALLEL ──────────────────
        // Convert UUIDs from command into domain value objects
        UserId userId = UserId.of(command.userId());

        // Run both lookups simultaneously with Mono.zip
        Mono<User> userMono = userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("AssignRole failed: user not found [{}]", command.userId());
                    return Mono.error(new IllegalArgumentException(
                        "User not found: " + command.userId()
                    ));
                }));

        // We find role by its UUID (admin selected from GET /roles list)
        Mono<Role> roleMono = roleRepository.findById(command.roleId().toString())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("AssignRole failed: role not found [{}]", command.roleId());
                    return Mono.error(new IllegalArgumentException(
                        "Role not found: " + command.roleId()
                    ));
                }));

        // ── Step 3: Both validated — assign the role ──────────────────────────
        // Mono.zip waits for both Monos to emit a value
        // If either emits an error, zip propagates that error immediately
        return Mono.zip(userMono, roleMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Role role = tuple.getT2();

                    log.debug("Assigning role [{}] to user [{}]",
                            role.getName(), user.getEmail());

                    return userRoleRepository.assignRole(user.getId(), role.getId());
                    // returns Mono<Void> — chain ends here
                    // Spring WebFlux maps Mono<Void> completion → HTTP 204 No Content
                });
    }
}
