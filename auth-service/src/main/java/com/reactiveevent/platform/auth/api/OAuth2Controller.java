package com.reactiveevent.platform.auth.api;

import com.reactiveevent.platform.common.api.auth.LoginResult;
import com.reactiveevent.platform.common.api.auth.OAuthCallbackCommand;
import com.reactiveevent.platform.common.application.auth.OAuthLoginUseCase;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * OAuth2Controller — handles Google and GitHub OAuth2 login flow.
 *
 * Base path: /auth/oauth2
 *
 * Two endpoints:
 *
 *   GET /auth/oauth2/{provider}/url
 *     → Returns the authorization URL to redirect the user's browser to.
 *     → The frontend calls this, then redirects the user to the returned URL.
 *     → Example response: { "url": "https://accounts.google.com/o/oauth2/auth?..." }
 *
 *   GET /auth/oauth2/{provider}/callback?code=...&state=...
 *     → Google/GitHub redirects here after user approves.
 *     → This endpoint exchanges the code, fetches profile, finds-or-creates user.
 *     → Returns LoginResult { accessToken, refreshToken } — same as /auth/login.
 *
 * Why GET for callback?
 *   OAuth2 providers send the callback as a browser redirect (GET).
 *   You cannot change this — it's part of the OAuth2 spec.
 *   The code and state arrive as query parameters, not a request body.
 *
 * {provider} path variable:
 *   Must be "google" or "github" (lowercase).
 *   We parse it to AuthProvider enum — invalid values return 400.
 */
@RestController
@RequestMapping("/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuthLoginUseCase oAuthLoginUseCase;

    // Google OAuth2 config
    @Value("${oauth2.google.auth-uri}")
    private String googleAuthUri;

    @Value("${oauth2.google.client-id}")
    private String googleClientId;

    @Value("${oauth2.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${oauth2.google.scope}")
    private String googleScope;

    // GitHub OAuth2 config
    @Value("${oauth2.github.auth-uri}")
    private String githubAuthUri;

    @Value("${oauth2.github.client-id}")
    private String githubClientId;

    @Value("${oauth2.github.redirect-uri}")
    private String githubRedirectUri;

    @Value("${oauth2.github.scope}")
    private String githubScope;

    // -------------------------------------------------------------------------
    // GET /auth/oauth2/{provider}/url
    // Returns the authorization URL that the frontend should redirect the user to.
    //
    // Example for Google:
    //   GET /auth/oauth2/google/url
    //   Response: { "url": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&..." }
    //
    // The frontend does: window.location.href = response.url
    // Then Google handles login and redirects back to your callback.
    //
    // Note on state parameter:
    //   In production, state should be a cryptographically random token stored
    //   in the user's session to prevent CSRF.
    //   Here we use a fixed "oauth2-state" for simplicity.
    //   Phase improvement: generate random state + store in Redis/session.
    // -------------------------------------------------------------------------
    @GetMapping("/{provider}/url")
    public Mono<ResponseEntity<AuthUrlResponse>> getAuthorizationUrl(
            @PathVariable String provider) {

        AuthProvider authProvider = parseProvider(provider);

        String url = switch (authProvider) {
            case GOOGLE -> UriComponentsBuilder
                    .fromUriString(googleAuthUri)
                    .queryParam("client_id",     googleClientId)
                    .queryParam("redirect_uri",  googleRedirectUri)
                    .queryParam("response_type", "code")
                    .queryParam("scope",         googleScope)
                    .queryParam("state",         "oauth2-state")
                    // access_type=offline → Google also returns a refresh token
                    .queryParam("access_type",   "offline")
                    .build()
                    .toUriString();

            case GITHUB -> UriComponentsBuilder
                    .fromUriString(githubAuthUri)
                    .queryParam("client_id",    githubClientId)
                    .queryParam("redirect_uri", githubRedirectUri)
                    .queryParam("scope",        githubScope)
                    .queryParam("state",        "oauth2-state")
                    .build()
                    .toUriString();

            case LOCAL -> throw new IllegalArgumentException(
                    "LOCAL provider does not have an OAuth2 authorization URL. "
                    + "Use POST /auth/login instead."
            );
        };

        return Mono.just(ResponseEntity.ok(new AuthUrlResponse(url)));
    }

    // -------------------------------------------------------------------------
    // GET /auth/oauth2/{provider}/callback?code=...&state=...
    // Called by Google/GitHub after the user approves.
    //
    // What this does:
    //   1. Parses provider from path variable
    //   2. Builds OAuthCallbackCommand from query params
    //   3. Delegates entirely to OAuthLoginUseCase
    //   4. Returns LoginResult { accessToken, refreshToken }
    //
    // The controller is thin — no business logic here.
    // -------------------------------------------------------------------------
    @GetMapping("/{provider}/callback")
    public Mono<ResponseEntity<LoginResult>> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false, defaultValue = "oauth2-state") String state) {

        AuthProvider authProvider = parseProvider(provider);

        OAuthCallbackCommand command = new OAuthCallbackCommand(code, state, authProvider);

        return oAuthLoginUseCase.login(command)
                .map(ResponseEntity::ok);
    }

    // -------------------------------------------------------------------------
    // parseProvider — converts the {provider} path variable to AuthProvider enum
    // "google" → AuthProvider.GOOGLE
    // "github" → AuthProvider.GITHUB
    // anything else → IllegalArgumentException → 400 Bad Request
    // -------------------------------------------------------------------------
    private AuthProvider parseProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "github" -> AuthProvider.GITHUB;
            default -> throw new IllegalArgumentException(
                "Unknown OAuth2 provider: '" + provider + "'. Supported: google, github"
            );
        };
    }

    // ── Response DTO ──────────────────────────────────────────────────────────
    // Simple record to wrap the authorization URL in a JSON object
    // { "url": "https://..." } — cleaner than returning a raw string
    private record AuthUrlResponse(String url) {}
}
