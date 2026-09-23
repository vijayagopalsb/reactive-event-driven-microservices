package com.reactiveevent.platform.common.domain.user;

/**
 * The lifecycle status of a user account.
 *
 * ACTIVE   → normal state, user can log in
 * INACTIVE → admin manually deactivated the account (reversible)
 * BLOCKED  → account flagged for security/abuse reasons (requires admin review to restore)
 *
 * These map directly to the `status` column in the `users` table.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
