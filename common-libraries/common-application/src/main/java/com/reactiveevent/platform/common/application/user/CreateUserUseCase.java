package com.reactiveevent.platform.common.application.user;

import com.reactiveevent.platform.common.api.user.CreateUserCommand;
import com.reactiveevent.platform.common.api.user.UserResponse;
import reactor.core.publisher.Mono;

/**
 * CreateUserUseCase — the contract for admin-initiated user creation.
 *
 * Business operation: "An admin wants to create a new LOCAL user account."
 *
 * Input:  CreateUserCommand  { email, rawPassword, role }
 * Output: UserResponse       { id, email, status, roles }
 *
 * What the implementation must do:
 *   1. Validate the email is not already taken
 *   2. Hash the raw password with BCrypt
 *   3. Create a new User aggregate (users table row)
 *   4. Create a UserProvider aggregate with LOCAL provider (user_providers row)
 *   5. Look up the roleId from the roles table by the role name
 *   6. Assign the role to the user (user_roles row)
 *   7. Register and return a UserCreatedEvent (for future event publishing)
 *   8. Return a UserResponse with the created user's details
 *
 * Access control:
 *   This use case is protected at the controller level with @PreAuthorize("hasRole('ADMIN')")
 *   The use case itself does not enforce security — that's the controller/security layer's job.
 *   This separation keeps the use case testable without a security context.
 *
 * Error cases:
 *   - Email already exists → throw a meaningful exception (not a generic error)
 *   - Role not found in DB → throw IllegalArgumentException
 */
public interface CreateUserUseCase {
    Mono<UserResponse> createUser(CreateUserCommand command);
}
