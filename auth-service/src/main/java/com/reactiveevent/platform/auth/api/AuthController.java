package com.reactiveevent.platform.auth.api;

import com.reactiveevent.platform.common.api.auth.LoginCommand;
import com.reactiveevent.platform.common.api.auth.LoginResult;
import com.reactiveevent.platform.common.application.auth.LoginUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * AuthController — handles authentication endpoints.
 *
 * Base path: /auth
 *
 * This controller is intentionally thin:
 *   - It receives HTTP input
 *   - Builds a command
 *   - Delegates to the use case
 *   - Returns the HTTP response
 *
 * No business logic lives here. Ever.
 *
 * Endpoints:
 *   POST /auth/login  → LOCAL email + password login
 *                       returns { accessToken, refreshToken }
 *
 * Note on OAuth2:
 *   Google and GitHub login go through separate endpoints (Phase 5):
 *   GET  /auth/oauth2/{provider}/url
 *   GET  /auth/oauth2/{provider}/callback
 *   These are handled by OAuth2Controller (to be added in Phase 5).
 *
 * Security:
 *   /auth/login is public — no JWT required (configured in SecurityConfig).
 *   All other paths require a valid JWT.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    // -------------------------------------------------------------------------
    // POST /auth/login
    // LOCAL login: email + password → JWT
    //
    // Request body example:
    //   { "email": "admin@example.com", "password": "Admin@1234", "provider": "LOCAL" }
    //
    // Response 200 OK:
    //   { "accessToken": { "value": "eyJ..." }, "refreshToken": { "value": "eyJ..." } }
    //
    // Error responses (handled by GlobalExceptionHandler):
    //   401 Unauthorized → invalid credentials
    //   403 Forbidden    → account inactive or blocked
    // -------------------------------------------------------------------------
    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResult>> login(@RequestBody LoginCommand command) {

        return loginUseCase.login(command)
                .map(ResponseEntity::ok);
        // ResponseEntity::ok = HTTP 200 OK with the LoginResult as the body
        // If loginUseCase returns an error, GlobalExceptionHandler handles it
    }
}
