package com.reactiveevent.platform.common.application.auth;

import com.reactiveevent.platform.common.api.auth.LoginResult;
import com.reactiveevent.platform.common.api.auth.OAuthCallbackCommand;
import reactor.core.publisher.Mono;

/**
 * OAuthLoginUseCase — the contract for Google and GitHub authentication.
 *
 * Business operation: "A user wants to log in via an external OAuth2 provider."
 *
 * Input:  OAuthCallbackCommand  { code, state, provider=GOOGLE|GITHUB }
 * Output: LoginResult           { accessToken, refreshToken }
 *
 * What the implementation must do:
 *   1. Validate the state token (CSRF check)
 *   2. Exchange the authorization code for an OAuth2 access token
 *      (server-to-server call to Google/GitHub — user never sees this)
 *   3. Use the access token to fetch the user's profile from the provider
 *      → Google: { sub, email, name, picture }
 *      → GitHub: { id, login, email, name, avatar_url }
 *   4. Find-or-create the user in your database:
 *      → Look up user_providers by (provider, external_id)
 *         FOUND:     returning user — load their data
 *         NOT FOUND: first login — auto-provision (create users + user_providers rows)
 *   5. Check the user's status is ACTIVE
 *   6. Load their roles and permissions from the RBAC tables
 *   7. Generate and return a signed JWT (access + refresh token)
 *
 * Key difference from LoginUseCase:
 *   LoginUseCase verifies a password you already have in your DB.
 *   OAuthLoginUseCase trusts the external provider — if Google says this is
 *   john@gmail.com, you trust that without any password check on your side.
 *
 * Auto-provisioning:
 *   If the user has never logged in before, their account is created silently.
 *   They get the USER role by default.
 *   No registration form. No email verification. Google/GitHub already did that.
 */
public interface OAuthLoginUseCase {
    Mono<LoginResult> login(OAuthCallbackCommand command);
}
