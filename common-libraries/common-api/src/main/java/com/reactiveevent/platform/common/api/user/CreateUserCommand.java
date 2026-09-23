package com.reactiveevent.platform.common.api.user;

import com.reactiveevent.platform.common.domain.user.UserRole;
import lombok.NonNull;

/**
 * CreateUserCommand — admin creates a new LOCAL user account.
 *
 * Who sends this command?
 *   Only an ADMIN, via POST /users.
 *   Regular users cannot create other users.
 *   OAuth2 users (Google/GitHub) are auto-provisioned — they don't go through this.
 *
 * Fields:
 *   email       → must be unique in the system (enforced by DB unique constraint)
 *   rawPassword → the plain-text password the admin sets for the user
 *                 the use case will hash it with BCrypt before storing
 *                 we call it "rawPassword" to make it obvious it's not yet hashed
 *   role        → initial role to assign (ADMIN, MANAGER, or USER)
 *                 the use case creates both the user row and the user_roles row
 *
 * Why rawPassword and not passwordHash?
 *   Commands carry what the caller provides.
 *   The caller (controller) receives a plain-text password from the HTTP request.
 *   Hashing is a business rule — it belongs in the use case, not in the controller.
 *   If the controller hashed it, we'd have business logic leaking into the API layer.
 *
 * Package: common.api.user (not common.api.auth)
 *   Login commands live in common.api.auth — they're about authentication.
 *   User management commands live in common.api.user — they're about the user resource.
 */
public record CreateUserCommand(
        @NonNull String email,
        @NonNull String rawPassword,
        @NonNull UserRole role
) {}
