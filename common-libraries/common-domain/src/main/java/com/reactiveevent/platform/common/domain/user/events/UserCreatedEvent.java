package com.reactiveevent.platform.common.domain.user.events;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.base.DomainEvent;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.Getter;
import lombok.NonNull;

/**
 * UserCreatedEvent — fired when a new user account is created.
 *
 * This event is raised in two scenarios:
 *   1. Admin creates a LOCAL user via POST /users
 *      → provider = LOCAL
 *
 *   2. A user logs in via Google or GitHub for the first time (auto-provisioning)
 *      → provider = GOOGLE or GITHUB
 *
 * Why include provider?
 *   Downstream consumers (audit log, analytics, welcome emails) need to know
 *   HOW the account was created. A welcome email to a self-onboarded Google user
 *   looks different from one sent to an admin-provisioned LOCAL user.
 *
 * Immutability:
 *   All fields are final — the past cannot change.
 *   occurredAt is set by the parent DomainEvent constructor to Instant.now().
 */
@Getter
public final class UserCreatedEvent extends DomainEvent {

    /** The ID of the newly created user. */
    @NonNull
    private final UserId userId;

    /** The email of the newly created user. */
    @NonNull
    private final String email;

    /**
     * The authentication provider used to create the account.
     * LOCAL  → admin created this user with a password
     * GOOGLE → user self-onboarded via Google OAuth2
     * GITHUB → user self-onboarded via GitHub OAuth2
     */
    @NonNull
    private final AuthProvider provider;

    public UserCreatedEvent(@NonNull UserId userId,
                            @NonNull String email,
                            @NonNull AuthProvider provider) {
        super(); // sets occurredAt = Instant.now()
        this.userId = userId;
        this.email = email;
        this.provider = provider;
    }

    @Override
    public String toString() {
        return "UserCreatedEvent{"
                + "userId=" + userId
                + ", email='" + email + "'"
                + ", provider=" + provider
                + ", occurredAt=" + getOccurredAt()
                + "}";
    }
}
