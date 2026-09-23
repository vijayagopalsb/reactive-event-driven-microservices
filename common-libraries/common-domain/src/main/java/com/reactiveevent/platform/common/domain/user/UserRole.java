package com.reactiveevent.platform.common.domain.user;

/**
 * The roles a user can be assigned.
 * This enum is used in commands (e.g. CreateUserCommand, AssignRoleCommand)
 * and in JWT claims to represent a user's role as a type-safe value.
 *
 * NOTE: This is NOT a field on the User entity.
 * Roles are a relationship: user → user_roles → roles (RBAC tables).
 * This enum simply mirrors the valid role names in the database.
 */
public enum UserRole {
    ADMIN,
    MANAGER,
    USER
}
