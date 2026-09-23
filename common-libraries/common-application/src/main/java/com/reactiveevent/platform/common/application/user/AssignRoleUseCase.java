package com.reactiveevent.platform.common.application.user;

import com.reactiveevent.platform.common.api.user.AssignRoleCommand;
import reactor.core.publisher.Mono;

/**
 * AssignRoleUseCase — the contract for assigning a role to a user.
 *
 * Business operation: "An admin wants to assign an existing role to an existing user."
 *
 * Input:  AssignRoleCommand  { userId, roleId }
 * Output: Mono<Void>         — no return value, just a completion signal
 *
 * What the implementation must do:
 *   1. Verify the user exists (userId is valid)
 *   2. Verify the role exists (roleId is valid)
 *   3. Check the assignment doesn't already exist (idempotent — assigning twice is OK,
 *      the DB unique constraint handles deduplication)
 *   4. Insert the user_roles row
 *
 * Why Mono<Void> and not Mono<UserResponse>?
 *   Role assignment is a side-effect operation — you're changing a relationship,
 *   not creating or returning a resource. The caller (controller) will return
 *   HTTP 204 No Content, which is the correct REST response for this case.
 *
 *   If the caller wants to see the updated user, they call GET /users/{id} separately.
 *   Mixing the concerns (assign + fetch) would make this use case do two things.
 *
 * Idempotency note:
 *   Calling assignRole with the same userId + roleId twice should not fail.
 *   The implementation handles the duplicate key case gracefully.
 *
 * Access control:
 *   Protected at the controller level with @PreAuthorize("hasRole('ADMIN')")
 */
public interface AssignRoleUseCase {
    Mono<Void> assignRole(AssignRoleCommand command);
}
