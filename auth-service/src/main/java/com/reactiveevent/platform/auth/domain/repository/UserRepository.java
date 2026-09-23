package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * UserRepository — read operations on the users table.
 *
 * This is a DOMAIN interface — it lives in the domain layer and knows
 * nothing about SQL, R2DBC, or Spring. It speaks only in domain types.
 *
 * The implementation (R2dbcUserRepository) lives in the infrastructure layer
 * and is the only place that knows how to translate these operations to SQL.
 *
 * Why only read operations here?
 *   Write operations (INSERT) are in WriteUserRepository.
 *   Separating reads from writes follows the CQRS principle:
 *   Command (write) and Query (read) responsibilities are separate.
 *   This makes each interface smaller and more focused.
 */
public interface UserRepository {

    /**
     * Find a user by their email address.
     * Used by: LoginUseCaseImpl to locate the user during login.
     * Returns Mono.empty() if no user exists with that email.
     */
    Mono<User> findByEmail(String email);

    /**
     * Find a user by their unique ID.
     * Used by: AssignRoleUseCaseImpl to verify the user exists before assigning a role.
     * Returns Mono.empty() if no user exists with that ID.
     */
    Mono<User> findById(UserId userId);

    /**
     * Load all users — for the admin user list endpoint.
     * Used by: UserManagementController GET /users
     */
    Flux<User> findAll();
}
