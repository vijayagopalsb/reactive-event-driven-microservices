package com.reactiveevent.platform.common.domain.user.events;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.base.DomainEvent;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.Getter;
import lombok.NonNull;

/**
 * UserLoggedInEvent — fired every time a user successfully logs in.
 *
 * Why track logins as domain events?
 *   - Audit trail: compliance systems need to know who logged in and when
 *   - Security: detecting unusual login patterns (new provider, new location)
 *   - Analytics: understanding login frequency per provider
 *
 * Why include provider?
 *   A user logging in via LOCAL (password) vs GOOGLE is meaningfully different:
 *   - Security teams watch for a user who always used LOCAL suddenly using GOOGLE
 *   - Rate limiting and brute-force detection applies only to LOCAL logins
 *   - Provider-specific session policies can be enforced downstream
 *
 * Immutability:
 *   All fields are final — a login event is a historical fact, it cannot be changed.
 *   occurredAt is set automatically by the DomainEvent base class.
 */
@Getter
public final class UserLoggedInEvent extends DomainEvent {

    /** The ID of the user who logged in. */
    @NonNull
    private final UserId userId;

    /** The email of the user who logged in. */
    @NonNull
    private final String email;

    /**
     * Which authentication method was used for this login.
     * LOCAL  → user provided email + password
     * GOOGLE → user authenticated via Google OAuth2
     * GITHUB → user authenticated via GitHub OAuth2
     */
    @NonNull
    private final AuthProvider provider;

    public UserLoggedInEvent(@NonNull UserId userId,
                             @NonNull String email,
                             @NonNull AuthProvider provider) {
        super(); // sets occurredAt = Instant.now()
        this.userId = userId;
        this.email = email;
        this.provider = provider;
    }

    @Override
    public String toString() {
        return "UserLoggedInEvent{"
                + "userId=" + userId
                + ", email='" + email + "'"
                + ", provider=" + provider
                + ", occurredAt=" + getOccurredAt()
                + "}";
    }
}
