package com.reactiveevent.platform.common.domain.user;

import com.reactiveevent.platform.common.domain.base.AggregateRoot;
import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;

/**
 * User — the core identity aggregate.
 *
 * Responsibility: answers "WHO is this person?"
 *   ✅ id        — unique identity
 *   ✅ email     — how they are identified in the system
 *   ✅ status    — are they allowed to use the system right now?
 *   ✅ createdAt — when was this account created?
 *
 *   ❌ passwordHash — NOT here. That belongs to UserProvider (HOW they authenticate).
 *   ❌ role         — NOT here. That belongs to user_roles → roles (WHAT they can do).
 *
 * Two factory methods:
 *   createNew()   — called when an admin creates a new user via API (generates new UUID)
 *   rehydrate()   — called when loading an existing user from the database (uses stored UUID)
 *
 * Why two methods instead of one constructor?
 *   createNew() sets createdAt = now and generates a new ID — it's a NEW user.
 *   rehydrate() takes the ID and createdAt from the DB — it's RESTORING an existing user.
 *   Using the same constructor for both would blur this important distinction.
 */
@Getter
public final class User extends AggregateRoot<UserId> {

    @NonNull
    private final String email;

    @NonNull
    private UserStatus status;

    // -------------------------------------------------------------------------
    // Private constructor — nobody outside this class can call new User(...)
    // All creation goes through the factory methods below.
    // This is the Factory Method pattern — it gives the class control over
    // how instances are created.
    // -------------------------------------------------------------------------
    private User(@NonNull UserId id,
                 @NonNull String email,
                 @NonNull UserStatus status) {
        super(id);
        this.email = email;
        this.status = status;
    }

    // -------------------------------------------------------------------------
    // Factory method 1: Create a brand new user
    // Called by: CreateUserUseCaseImpl when admin creates a user via POST /users
    //
    // Generates a new random UUID as the user's identity.
    // Status defaults to ACTIVE — new users are ready to use the system.
    // -------------------------------------------------------------------------
    public static User createNew(@NonNull String email) {
        return new User(
                UserId.newId(),     // generates a random UUID
                email,
                UserStatus.ACTIVE   // always starts as ACTIVE
        );
    }

    // -------------------------------------------------------------------------
    // Factory method 2: Rehydrate a user from the database
    // Called by: R2dbcUserRepository when loading a user row
    //
    // "Rehydrate" = take dry data (DB row) and restore it to a live domain object.
    // We pass in the existing ID and status from the DB — we do NOT generate
    // a new ID here. That would be a serious bug (different ID each time you load).
    // -------------------------------------------------------------------------
    public static User rehydrate(@NonNull UserId id,
                                 @NonNull String email,
                                 @NonNull UserStatus status) {
        return new User(id, email, status);
    }

    // -------------------------------------------------------------------------
    // Behaviour methods — things a User can DO
    // Note: each method calls touch() from BaseEntity which updates updatedAt.
    // -------------------------------------------------------------------------

    /**
     * Deactivate this user — they can no longer log in.
     * Called by: admin via PATCH /users/{id}/status
     */
    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.touch();
    }

    /**
     * Reactivate a previously deactivated user.
     */
    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.touch();
    }

    /**
     * Block this user — stronger than deactivate.
     * Used for security violations, abuse, etc.
     */
    public void block() {
        this.status = UserStatus.BLOCKED;
        this.touch();
    }

    /**
     * Check if the user is allowed to log in.
     * Called by: LoginUseCaseImpl before issuing a token.
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "User{id=" + getId() + ", email='" + email + "', status=" + status + "}";
    }
}
