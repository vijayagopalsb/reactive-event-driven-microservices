package com.reactiveevent.platform.common.api.user;

import lombok.NonNull;

import java.util.UUID;

/**
 * AssignRoleCommand — admin assigns a role to a user.
 *
 * Who sends this command?
 *   Only an ADMIN, via POST /users/{userId}/roles/{roleId}.
 *
 * Fields:
 *   userId → the user receiving the role
 *   roleId → the role being assigned
 *
 * Why UUID and not UserId/RoleId value objects here?
 *   Commands live in common-api which is a thin contract layer.
 *   Value objects live in common-domain.
 *   We keep commands free of domain type dependencies where possible.
 *   The use case converts UUID → UserId and UUID → RoleId internally.
 *
 *   Exception: CreateUserCommand uses UserRole enum because it's also
 *   in common-domain and the role name (ADMIN/MANAGER/USER) is the
 *   natural way for an API caller to specify a role.
 *   Here we use roleId (UUID) because the caller selects from a list
 *   returned by GET /roles which returns IDs.
 */
public record AssignRoleCommand(
        @NonNull UUID userId,
        @NonNull UUID roleId
) {}
