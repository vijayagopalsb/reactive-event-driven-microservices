package com.reactiveevent.platform.auth.infrastructure.permission;

import com.reactiveevent.platform.auth.domain.repository.PermissionRepository;
import com.reactiveevent.platform.common.domain.permission.Permission;
import com.reactiveevent.platform.common.domain.permission.PermissionId;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * R2dbcPermissionRepository — reads from the permissions, role_permissions,
 * and user_roles tables to resolve a user's effective permissions.
 *
 * The full RBAC resolution chain:
 *   user_roles (user_id → role_id)
 *     JOIN roles (id)
 *     JOIN role_permissions (role_id → permission_id)
 *     JOIN permissions (id)
 *
 * One query walks the entire chain and returns all permissions for a user.
 * This is efficient — one DB round-trip instead of multiple.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcPermissionRepository implements PermissionRepository {

    private final DatabaseClient client;

    // -------------------------------------------------------------------------
    // findPermissionsByUserId
    // Walks the full RBAC chain in one SQL query.
    // Used by:
    //   - TokenGeneratorImpl to embed permissions in JWT claims
    //   - CustomReactiveUserDetailsService for Spring Security authorities
    // -------------------------------------------------------------------------
    @Override
    public Flux<Permission> findPermissionsByUserId(UserId userId) {
        return client.sql("""
                SELECT DISTINCT p.id, p.name
                FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN user_roles ur       ON ur.role_id       = rp.role_id
                WHERE ur.user_id = :userId
                """)
                // DISTINCT prevents duplicate permissions if a user has multiple
                // roles that share the same permission (e.g. ADMIN + MANAGER both have USER_READ)
                .bind("userId", userId.getValue().toString())
                .map(row -> {
                    String idStr = row.get("id", String.class);
                    String name  = row.get("name", String.class);

                    if (idStr == null || name == null) {
                        throw new IllegalStateException(
                            "Corrupt permissions row: missing required fields"
                        );
                    }

                    return Permission.rehydrate(
                            PermissionId.of(UUID.fromString(idStr)),
                            name
                    );
                })
                .all();
    }
}
