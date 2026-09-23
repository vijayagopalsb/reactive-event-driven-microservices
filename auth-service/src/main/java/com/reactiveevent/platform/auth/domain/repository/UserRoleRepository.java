package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.role.RoleId;
import com.reactiveevent.platform.common.domain.user.UserId;
import reactor.core.publisher.Mono;

/**
 * UserRoleRepository — write operations on the user_roles join table.
 *
 * The user_roles table is a many-to-many join between users and roles.
 * It has no surrogate ID — the composite (user_id, role_id) is the primary key.
 *
 * Why a separate interface for this table?
 *   user_roles is not a domain aggregate — it's a relationship.
 *   It doesn't belong in UserRepository (that's about the user identity)
 *   or RoleRepository (that's about reading roles).
 *   Its own interface keeps the responsibilities clean.
 *
 * Why only assignRole here and not removeRole?
 *   We implement what we need now.
 *   removeRole will be added when we build PATCH /users/{id}/roles/{roleId} (DELETE).
 *   YAGNI — You Aren't Gonna Need It (until you do).
 */
public interface UserRoleRepository {

    /**
     * Insert a row into user_roles: assign roleId to userId.
     *
     * Idempotent: if the assignment already exists, do nothing.
     * Uses INSERT IGNORE to handle the duplicate key case gracefully
     * instead of throwing an error.
     *
     * Returns Mono<Void> — no meaningful return value for this operation.
     */
    Mono<Void> assignRole(UserId userId, RoleId roleId);
}
