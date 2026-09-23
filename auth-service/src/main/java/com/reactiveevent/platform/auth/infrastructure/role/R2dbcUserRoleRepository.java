package com.reactiveevent.platform.auth.infrastructure.role;

import com.reactiveevent.platform.auth.domain.repository.UserRoleRepository;
import com.reactiveevent.platform.common.domain.role.RoleId;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * R2dbcUserRoleRepository — writes to the user_roles join table.
 *
 * INSERT IGNORE explained:
 *   Normal INSERT fails with a duplicate key error if the row exists.
 *   INSERT IGNORE silently skips the insert if the row already exists.
 *   This gives us idempotency: assigning a role twice is not an error.
 *
 *   This is MySQL-specific syntax. For PostgreSQL you'd use:
 *   INSERT INTO user_roles ... ON CONFLICT DO NOTHING
 *
 * Why .then() at the end?
 *   .fetch().rowsUpdated() returns Mono<Long> (how many rows were affected).
 *   We don't care about the number — 0 means it already existed (OK),
 *   1 means it was just inserted (OK).
 *   .then() converts Mono<Long> → Mono<Void> (signals completion, no value).
 */
@Repository
@RequiredArgsConstructor
public class R2dbcUserRoleRepository implements UserRoleRepository {

    private final DatabaseClient client;

    @Override
    public Mono<Void> assignRole(UserId userId, RoleId roleId) {
        return client.sql("""
                INSERT IGNORE INTO user_roles (user_id, role_id)
                VALUES (:userId, :roleId)
                """)
                .bind("userId", userId.getValue().toString())
                .bind("roleId", roleId.getValue().toString())
                .fetch()
                .rowsUpdated()
                // Convert Mono<Long> → Mono<Void>
                // The caller (use case) doesn't need to know the row count
                .then();
    }
}
