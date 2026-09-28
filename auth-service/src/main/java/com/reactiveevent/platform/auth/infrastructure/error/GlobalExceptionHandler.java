package com.reactiveevent.platform.auth.infrastructure.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler — maps domain exceptions to HTTP responses.
 *
 * Why centralize exception handling here?
 *   Without this, Spring would return a generic 500 Internal Server Error
 *   for every exception, which gives the client no useful information.
 *
 *   With this, each exception type maps to the correct HTTP status:
 *     IllegalArgumentException → 400 Bad Request (invalid input) or
 *                                401 Unauthorized (invalid credentials)
 *     IllegalStateException    → 409 Conflict or 400 (account status issues)
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   It intercepts exceptions thrown from any @RestController in the application.
 *
 * Security note:
 *   We return the exception message to the client only for expected errors.
 *   For unexpected errors (RuntimeException), we log the full stack trace
 *   but return only a generic message to avoid leaking internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestBody(ServerWebInputException ex) {
        log.warn("Invalid request body: {}", ex.getReason());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "Invalid request body or field value. Supported providers: LOCAL, GOOGLE, GITHUB"
                ));
    }

    // -------------------------------------------------------------------------
    // IllegalArgumentException
    // Thrown by: LoginUseCaseImpl ("Invalid credentials")
    //            CreateUserUseCaseImpl ("Email already registered")
    //            AssignRoleUseCaseImpl ("User not found", "Role not found")
    //
    // Why 400 for most and 401 for credentials?
    //   "Invalid credentials" is an authentication failure → 401
    //   "Email already registered" is a validation failure → 400
    //   "User not found" / "Role not found" → 400 (bad input)
    //   We check the message to distinguish these cases.
    // -------------------------------------------------------------------------
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {

        String message = ex.getMessage();

        // Authentication failures → 401
        if (message != null && message.equals("Invalid credentials")) {
            log.warn("Authentication failed: {}", message);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid credentials"));
        }

        // All other argument errors → 400
        log.warn("Bad request: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message != null ? message : "Bad request"));
    }

    // -------------------------------------------------------------------------
    // IllegalStateException
    // Thrown by: LoginUseCaseImpl when account is INACTIVE or BLOCKED
    //            Repository classes for corrupt DB rows (internal error)
    // -------------------------------------------------------------------------
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {

        String message = ex.getMessage();

        // Account status errors — client-facing
        if (message != null && (message.startsWith("Account is"))) {
            log.warn("Account access denied: {}", message);
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(message));
        }

        // Everything else is an internal error — log fully, return generic message
        log.error("Internal state error: {}", message, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An internal error occurred"));
    }

    // -------------------------------------------------------------------------
    // Catch-all for unexpected errors
    // We never expose internal error details to the client.
    // -------------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred"));
    }

    // ── Error response DTO ────────────────────────────────────────────────────
    private record ErrorResponse(String message) {}
}
