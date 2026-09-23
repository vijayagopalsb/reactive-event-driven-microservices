package com.reactiveevent.platform.common.api.user;

import com.reactiveevent.platform.common.domain.user.UserStatus;
import lombok.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * UserResponse — what the API returns when a caller asks about a user.
 *
 * This is a DTO (Data Transfer Object) — it carries data OUT of the system.
 * Commands carry data IN. Responses carry data OUT.
 *
 * Why not return the User domain entity directly?
 *   The domain entity is an internal concept. It might contain fields you don't
 *   want to expose (internal IDs, audit fields), or lack fields the API caller
 *   needs (like the list of role names resolved from the RBAC tables).
 *
 *   The response is shaped for the API consumer, not for the domain.
 *   This separation also means you can change the domain model without
 *   breaking the API contract, and vice versa.
 *
 * Fields:
 *   id     → the user's UUID (as String for JSON serialization simplicity)
 *   email  → the user's email address
 *   status → ACTIVE, INACTIVE, or BLOCKED
 *   roles  → list of role names this user has been assigned (e.g. ["ADMIN", "USER"])
 *
 * Note: password is intentionally NOT included — never expose hashes in responses.
 */
public record UserResponse(
        @NonNull UUID id,
        @NonNull String email,
        @NonNull UserStatus status,
        @NonNull List<String> roles
) {}
