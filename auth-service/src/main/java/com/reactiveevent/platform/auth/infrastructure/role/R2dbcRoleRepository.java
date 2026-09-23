package com.reactiveevent.platform.auth.infrastructure.role;

import com.reactiveevent.platform.auth.domain.repository.RoleRepository;
import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.role.RoleId;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2dbcRoleRepository — reads from the roles and user_roles tables.
 *
 * Two operations:
 *   findRolesByUserId  → walks user_roles → roles to get a user's assigned roles
 *   findByName         → looks up a role by its name (e.g. "ADMIN")
 *                        used by CreateUserUseCaseImpl to find the roleId to assign
 */
@Repository
@RequiredArgsConstructor
public class R2dbcRoleRepository implements RoleRepository {

    private final DatabaseClient client;

    // -------------------------------------------------------------------------
    // findRolesByUserId
    // JOIN: user_roles → roles
    // Used by:
    //   - TokenGeneratorImpl (to put roles in the JWT)
    //   - CustomReactiveUserDetailsService (Spring Security context)
    // -------------------------------------------------------------------------
    @Override
    public Flux<Role> findRolesByUserId(UserId userId) {
        return client.sql("""
                SELECT r.id, r.name
                FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = :userId
                """)
                // Bind as String — MySQL stores UUIDs as CHAR(36)
                .bind("userId", userId.getValue().toString())
                .map(row -> mapRow(row))
                .all();
    }

    // -------------------------------------------------------------------------
    // findByName
    // Used by CreateUserUseCaseImpl:
    //   The command carries a UserRole enum (e.g. UserRole.ADMIN).
    //   We need the actual roleId UUID from the roles table to create the
    //   user_roles row. This lookup bridges the enum to the DB row.
    // -------------------------------------------------------------------------
    @Override
    public Mono<Role> findByName(String name) {
        return client.sql("""
                SELECT id, name
                FROM roles
                WHERE name = :name
                """)
                .bind("name", name)
                .map(row -> mapRow(row))
                .one();
    }

    // -------------------------------------------------------------------------
    // findById
    // Used by AssignRoleUseCaseImpl:
    //   The command carries a roleId UUID (admin selected from GET /roles).
    //   We validate the role exists before inserting into user_roles.
    // -------------------------------------------------------------------------
    @Override
    public Mono<Role> findById(String roleId) {
        return client.sql("""
                SELECT id, name
                FROM roles
                WHERE id = :id
                """)
                .bind("id", roleId)
                .map(row -> mapRow(row))
                .one();
    }

    // -------------------------------------------------------------------------
    // findAll — used by GET /roles to list all available roles
    // -------------------------------------------------------------------------
    @Override
    public Flux<Role> findAll() {
        return client.sql("""
                SELECT id, name
                FROM roles
                ORDER BY name ASC
                """)
                .map(row -> mapRow(row))
                .all();
    }

    // -------------------------------------------------------------------------
    // mapRow — shared helper to avoid duplicating row mapping logic
    // Uses Readable (not Row) — Spring Data R2DBC 3.x changed .map() callback type
    // -------------------------------------------------------------------------
    private Role mapRow(io.r2dbc.spi.Readable row) {
        String idStr = row.get("id", String.class);
        String name  = row.get("name", String.class);

        if (idStr == null || name == null) {
            throw new IllegalStateException("Corrupt roles row: missing required fields");
        }

        return Role.rehydrate(
                RoleId.of(UUID.fromString(idStr)),
                name
        );
    }
}
