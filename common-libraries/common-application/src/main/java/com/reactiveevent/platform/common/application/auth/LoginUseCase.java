package com.reactiveevent.platform.common.application.auth;

import com.reactiveevent.platform.common.api.auth.LoginCommand;
import com.reactiveevent.platform.common.api.auth.LoginResult;
import reactor.core.publisher.Mono;

/**
 * LoginUseCase — the contract for LOCAL authentication.
 *
 * Business operation: "A user wants to log in with email and password."
 *
 * Input:  LoginCommand  { email, password, provider=LOCAL }
 * Output: LoginResult   { accessToken, refreshToken }
 *
 * What the implementation must do:
 *   1. Find the user by email
 *   2. Load their LOCAL user_providers row (the one with the password hash)
 *   3. Verify the raw password against the stored BCrypt hash
 *   4. Check the user's status is ACTIVE
 *   5. Load their roles and permissions from the RBAC tables
 *   6. Generate and return a signed JWT (access + refresh token)
 *
 * Error cases (implementation must handle):
 *   - Email not found           → invalid credentials (don't reveal which is wrong)
 *   - Password does not match   → invalid credentials
 *   - User is INACTIVE/BLOCKED  → account not accessible
 *   - No LOCAL provider exists  → user exists but uses OAuth2 only
 *
 * Note: OAuth2 login (Google/GitHub) uses OAuthLoginUseCase — separate interface.
 */
public interface LoginUseCase {
    Mono<LoginResult> login(LoginCommand command);
}
