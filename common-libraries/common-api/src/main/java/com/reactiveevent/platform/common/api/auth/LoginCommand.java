package com.reactiveevent.platform.common.api.auth;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import lombok.NonNull;

/**
 * LoginCommand — the input for the login use case.
 *
 * Used for LOCAL login only (email + password).
 * OAuth2 login (Google, GitHub) uses a separate OAuthCallbackCommand
 * because the flow and required fields are completely different.
 *
 * Fields:
 *   email    → the user's email address (always required)
 *   password → the raw password (required for LOCAL, must be verified against hash)
 *   provider → which authentication method (LOCAL / GOOGLE / GITHUB)
 *
 * Why is password @NonNull here?
 *   This command is only used for LOCAL login.
 *   OAuth2 has its own command (OAuthCallbackCommand).
 *   Separating them means each command is always fully valid — no nullable fields
 *   that are "only required sometimes". That's cleaner than one command with
 *   optional fields that callers must remember to check.
 *
 * Validation note:
 *   Records don't validate by default. The use case is responsible for
 *   checking that the email exists and the password matches the stored hash.
 */
public record LoginCommand(
        @NonNull String email,
        @NonNull String password,
        @NonNull AuthProvider provider
) {}
