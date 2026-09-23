package com.reactiveevent.platform.common.api.user;

import lombok.NonNull;

import java.util.UUID;

/**
 * RoleResponse — what the API returns when listing available roles.
 *
 * Used by: GET /roles
 * The admin calls this to see which roleIds exist before calling
 * POST /users/{id}/roles to assign one.
 *
 * Why not just return the role name?
 *   The AssignRoleCommand takes a roleId (UUID), not a name.
 *   So the caller needs both the name (to display) and the id (to send).
 *   This response gives them both.
 */
public record RoleResponse(
        @NonNull UUID id,
        @NonNull String name
) {}
