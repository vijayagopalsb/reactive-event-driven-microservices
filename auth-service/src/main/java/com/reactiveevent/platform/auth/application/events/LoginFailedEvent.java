package com.reactiveevent.platform.auth.application.events;

import java.time.Instant;

public record LoginFailedEvent(
        String eventType,
        int schemaVersion,
        String provider,
        String reason,
        Instant occurredAt
) {
    public static LoginFailedEvent invalidLocalCredentials() {
        return new LoginFailedEvent(
                "LoginFailed",
                1,
                "LOCAL",
                "INVALID_CREDENTIALS",
                Instant.now()
        );
    }
}
