package com.reactiveevent.platform.common.api.auth;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import lombok.NonNull;

/**
 * OAuthCallbackCommand — the input for the OAuth2 login use case.
 *
 * This command is built when Google or GitHub redirects back to your
 * callback URL after the user grants permission.
 *
 * The OAuth2 Authorization Code flow (recap):
 *   1. User clicks "Login with Google"
 *   2. Redirected to Google's login page
 *   3. User approves → Google redirects back to:
 *      GET /auth/oauth2/google/callback?code=ABC123&state=XYZ
 *   4. Controller extracts code + state, builds this command
 *   5. Use case exchanges the code for a user profile, then issues YOUR JWT
 *
 * Fields:
 *   code     → the one-time authorization code from Google/GitHub
 *              your backend exchanges this for an access token (server-to-server)
 *   state    → the CSRF token you sent in step 1, must match what's in session
 *              prevents Cross-Site Request Forgery attacks
 *   provider → GOOGLE or GITHUB (never LOCAL — LOCAL uses LoginCommand)
 *
 * Why NOT include email or password here?
 *   At this point we don't know the user's email yet.
 *   The email comes AFTER we exchange the code and fetch the user's profile.
 *   The use case handles that exchange — the command just carries the raw inputs.
 */
public record OAuthCallbackCommand(
        @NonNull String code,
        @NonNull String state,
        @NonNull AuthProvider provider
) {}
