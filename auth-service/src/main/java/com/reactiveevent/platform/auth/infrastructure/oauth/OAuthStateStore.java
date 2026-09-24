package com.reactiveevent.platform.auth.infrastructure.oauth;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores short-lived OAuth state values and consumes them exactly once.
 *
 * The cookie set by the controller binds the state to the browser that started
 * the flow. The in-memory store is suitable for a single instance; clustered
 * deployments should replace this component with a shared reactive store.
 */
@Component
public class OAuthStateStore {

    public static final String COOKIE_NAME = "oauth2_state";
    public static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

    public Mono<String> create(AuthProvider provider) {
        return Mono.fromSupplier(() -> {
            purgeExpired();

            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            states.put(hash(state), new StateEntry(provider, Instant.now().plus(STATE_TTL)));
            return state;
        });
    }

    public Mono<Void> consume(String state, String cookieState, AuthProvider provider) {
        return Mono.defer(() -> {
            if (state == null || cookieState == null || !constantTimeEquals(state, cookieState)) {
                return Mono.<Void>error(new IllegalArgumentException("Invalid OAuth state"));
            }

            StateEntry entry = states.remove(hash(state));
            if (entry == null || entry.expiresAt().isBefore(Instant.now())
                    || entry.provider() != provider) {
                return Mono.<Void>error(new IllegalArgumentException("Invalid OAuth state"));
            }

            return Mono.<Void>empty();
        });
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                right.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private record StateEntry(AuthProvider provider, Instant expiresAt) {
    }
}
