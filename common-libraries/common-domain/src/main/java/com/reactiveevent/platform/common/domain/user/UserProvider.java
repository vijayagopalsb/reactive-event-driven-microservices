package com.reactiveevent.platform.common.domain.user;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.base.AggregateRoot;
import lombok.Getter;
import lombok.NonNull;

/**
 * UserProvider — how a user authenticates.
 *
 * Maps to the `user_providers` table.
 *
 * Responsibility: answers "HOW does this user prove their identity?"
 *   ✅ provider     — which method: LOCAL, GOOGLE, or GITHUB
 *   ✅ externalId   — the OAuth2 provider's unique ID for this user (null for LOCAL)
 *   ✅ passwordHash — bcrypt hash (null for GOOGLE/GITHUB, only set for LOCAL)
 *   ✅ userId       — which User this provider belongs to
 *
 * Why are externalId and passwordHash nullable?
 *   LOCAL  provider: has passwordHash, no externalId
 *   GOOGLE provider: has externalId (Google's "sub"), no passwordHash
 *   GITHUB provider: has externalId (GitHub's numeric user id), no passwordHash
 *
 * Why is this an AggregateRoot and not just a child of User?
 *   In reactive systems with R2DBC there is no lazy loading.
 *   Loading a User would force loading ALL their providers every time — expensive.
 *   Treating UserProvider as its own aggregate root lets us load it only when needed
 *   (e.g. during login). The User aggregate is kept lean.
 *
 * Factory methods:
 *   createLocal(userId, passwordHash)              → for LOCAL login (admin creates user)
 *   createOAuth(userId, provider, externalId)      → for Google/GitHub (first OAuth login)
 *   rehydrate(id, userId, provider, externalId, passwordHash) → load from DB
 */
@Getter
public final class UserProvider extends AggregateRoot<UserProviderId> {

    // Which User does this provider belong to?
    // This is a reference by ID — we don't embed the whole User object here.
    // In DDD, aggregates reference each other by ID, not by object reference.
    @NonNull
    private final UserId userId;

    // Which authentication method: LOCAL, GOOGLE, or GITHUB
    @NonNull
    private final AuthProvider provider;

    // OAuth2 provider's unique user ID.
    // Google: the "sub" field from the ID token (e.g. "109876543210")
    // GitHub: the numeric user ID (e.g. "12345678")
    // LOCAL:  null — local users don't have an external ID
    private final String externalId;

    // BCrypt password hash. Only set for LOCAL provider.
    // GOOGLE and GITHUB users have no password in our system.
    private String passwordHash;

    // -------------------------------------------------------------------------
    // Private constructor
    // All creation goes through the factory methods below.
    // -------------------------------------------------------------------------
    private UserProvider(@NonNull UserProviderId id,
                         @NonNull UserId userId,
                         @NonNull AuthProvider provider,
                         String externalId,
                         String passwordHash) {
        super(id);
        this.userId = userId;
        this.provider = provider;
        this.externalId = externalId;
        this.passwordHash = passwordHash;
    }

    // -------------------------------------------------------------------------
    // Factory method 1: Create a LOCAL provider entry
    // Called when: admin creates a user via POST /users with a password
    //
    // LOCAL users have a password but no externalId.
    // -------------------------------------------------------------------------
    public static UserProvider createLocal(@NonNull UserId userId,
                                           @NonNull String passwordHash) {
        return new UserProvider(
                UserProviderId.newId(),
                userId,
                AuthProvider.LOCAL,
                null,           // no externalId for LOCAL
                passwordHash
        );
    }

    // -------------------------------------------------------------------------
    // Factory method 2: Create an OAuth2 provider entry
    // Called when: a user logs in via Google or GitHub for the first time
    //              (auto-provisioning — no prior registration needed)
    //
    // OAuth2 users have an externalId but no password.
    // -------------------------------------------------------------------------
    public static UserProvider createOAuth(@NonNull UserId userId,
                                           @NonNull AuthProvider provider,
                                           @NonNull String externalId) {

        // Guard: this method is only for OAuth2 providers, not LOCAL
        if (provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException(
                "Use createLocal() for LOCAL provider, not createOAuth()"
            );
        }

        return new UserProvider(
                UserProviderId.newId(),
                userId,
                provider,
                externalId,
                null            // no passwordHash for OAuth2
        );
    }

    // -------------------------------------------------------------------------
    // Factory method 3: Rehydrate from the database
    // Called by: R2dbcUserProviderRepository when loading a row
    //
    // All values come from the DB — we restore them exactly as stored.
    // -------------------------------------------------------------------------
    public static UserProvider rehydrate(@NonNull UserProviderId id,
                                         @NonNull UserId userId,
                                         @NonNull AuthProvider provider,
                                         String externalId,
                                         String passwordHash) {
        return new UserProvider(id, userId, provider, externalId, passwordHash);
    }

    // -------------------------------------------------------------------------
    // Behaviour: update password (LOCAL provider only)
    // Called when: admin resets a user's password
    // -------------------------------------------------------------------------
    public void updatePassword(@NonNull String newPasswordHash) {
        if (this.provider != AuthProvider.LOCAL) {
            throw new IllegalStateException(
                "Cannot set a password on a " + provider + " provider"
            );
        }
        this.passwordHash = newPasswordHash;
        this.touch();
    }

    // -------------------------------------------------------------------------
    // Helper: does this provider have a password?
    // Used by: PasswordVerifierImpl to check before attempting verification
    // -------------------------------------------------------------------------
    public boolean hasPassword() {
        return this.passwordHash != null && !this.passwordHash.isBlank();
    }

    @Override
    public String toString() {
        return "UserProvider{id=" + getId()
                + ", userId=" + userId
                + ", provider=" + provider
                + ", externalId=" + externalId + "}";
    }
}
