package com.reactiveevent.platform.auth.infrastructure.provider;

import com.reactiveevent.platform.auth.domain.repository.UserProviderRepository;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import com.reactiveevent.platform.common.domain.user.UserProviderId;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2dbcUserProviderRepository — reads and writes to the user_providers table.
 *
 * This repository serves three main callers:
 *
 *   1. LoginUseCaseImpl (LOCAL login)
 *      → findByEmailAndProvider(email, LOCAL)
 *      → gets the password hash to verify against
 *
 *   2. OAuthLoginUseCaseImpl (Google/GitHub login) — Phase 5
 *      → findByProviderAndExternalId(GOOGLE, "sub123")
 *      → determines if it's a returning or first-time user
 *
 *   3. CreateUserUseCaseImpl (admin creates user)
 *      → save(UserProvider.createLocal(userId, hashedPassword))
 *      → stores the LOCAL provider row
 *
 * SQL patterns used:
 *   findByEmailAndProvider  → JOIN users ON users.id = up.user_id WHERE email + provider
 *   findByProviderAndExternalId → WHERE provider + external_id (no join needed)
 *   findByUserIdAndProvider → WHERE user_id + provider
 *   save                    → INSERT INTO user_providers
 */
@Repository
@RequiredArgsConstructor
public class R2dbcUserProviderRepository implements UserProviderRepository {

    private final DatabaseClient client;

    // -------------------------------------------------------------------------
    // findByEmailAndProvider
    // Used during LOCAL login.
    // We know the email (from LoginCommand) and we want the LOCAL provider row.
    // JOIN users to match by email, then filter by provider.
    // -------------------------------------------------------------------------
    @Override
    public Mono<UserProvider> findByEmailAndProvider(String email, AuthProvider provider) {
        return client.sql("""
                SELECT up.id, up.user_id, up.provider, up.external_id, up.password_hash
                FROM user_providers up
                JOIN users u ON u.id = up.user_id
                WHERE u.email    = :email
                  AND up.provider = :provider
                """)
                .bind("email",    email)
                .bind("provider", provider.name())
                .map(row -> mapRow(row))
                .one();
        // Returns Mono.empty() if no matching row — LOGIN will treat this as
        // "invalid credentials" (don't reveal whether email exists or provider mismatch)
    }

    // -------------------------------------------------------------------------
    // findByProviderAndExternalId
    // Used during OAuth2 login (Phase 5).
    // We have the provider's unique user ID (Google sub, GitHub numeric id).
    // This tells us if this OAuth2 account has ever logged into our system.
    // -------------------------------------------------------------------------
    @Override
    public Mono<UserProvider> findByProviderAndExternalId(AuthProvider provider, String externalId) {
        return client.sql("""
                SELECT id, user_id, provider, external_id, password_hash
                FROM user_providers
                WHERE provider    = :provider
                  AND external_id = :externalId
                """)
                .bind("provider",   provider.name())
                .bind("externalId", externalId)
                .map(row -> mapRow(row))
                .one();
        // Returns Mono.empty() → first login, user needs to be auto-provisioned
        // Returns a value    → returning user, just issue JWT
    }

    // -------------------------------------------------------------------------
    // findByUserIdAndProvider
    // Used when we have a UserId and need the specific provider row.
    // -------------------------------------------------------------------------
    @Override
    public Mono<UserProvider> findByUserIdAndProvider(UserId userId, AuthProvider provider) {
        return client.sql("""
                SELECT id, user_id, provider, external_id, password_hash
                FROM user_providers
                WHERE user_id  = :userId
                  AND provider = :provider
                """)
                .bind("userId",   userId.getValue().toString())
                .bind("provider", provider.name())
                .map(row -> mapRow(row))
                .one();
    }

    // -------------------------------------------------------------------------
    // save — INSERT a new user_providers row
    // Called when:
    //   - Admin creates a LOCAL user
    //   - OAuth2 user logs in for the first time (auto-provisioning, Phase 5)
    //
    // R2DBC rule for nullable columns:
    //   .bind("name", value)        → use when value is NOT null
    //   .bindNull("name", Type)     → use when value IS null
    //   NEVER call both on the same parameter — that throws at runtime.
    //
    // We build the spec step by step using a local variable so we can
    // conditionally chain the right call for each nullable field.
    // -------------------------------------------------------------------------
    @Override
    public Mono<UserProvider> save(UserProvider userProvider) {

        DatabaseClient.GenericExecuteSpec spec = client.sql("""
                INSERT INTO user_providers (id, user_id, provider, external_id, password_hash, created_at)
                VALUES (:id, :userId, :provider, :externalId, :passwordHash, NOW())
                """)
                .bind("id",       userProvider.getId().getValue().toString())
                .bind("userId",   userProvider.getUserId().getValue().toString())
                .bind("provider", userProvider.getProvider().name());

        // external_id: null for LOCAL, provider's user ID for GOOGLE/GITHUB
        if (userProvider.getExternalId() != null) {
            spec = spec.bind("externalId", userProvider.getExternalId());
        } else {
            spec = spec.bindNull("externalId", String.class);
        }

        // password_hash: bcrypt hash for LOCAL, null for GOOGLE/GITHUB
        if (userProvider.getPasswordHash() != null) {
            spec = spec.bind("passwordHash", userProvider.getPasswordHash());
        } else {
            spec = spec.bindNull("passwordHash", String.class);
        }

        return spec.fetch()
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new IllegalStateException(
                            "INSERT into user_providers failed — 0 rows affected"
                        ));
                    }
                    return Mono.just(userProvider);
                });
    }

    // -------------------------------------------------------------------------
    // mapRow — shared helper to map a ResultRow → UserProvider domain object
    // Private because nothing outside this class should know how rows map.
    //
    // Uses io.r2dbc.spi.Readable (not Row) — Spring Data R2DBC 3.x changed
    // the .map() callback to pass Readable instead of Row.
    // Readable is the parent interface with the same get() methods we use.
    // -------------------------------------------------------------------------
    private UserProvider mapRow(io.r2dbc.spi.Readable row) {
        String idStr     = row.get("id", String.class);
        String userIdStr = row.get("user_id", String.class);
        String provStr   = row.get("provider", String.class);
        String extId     = row.get("external_id", String.class);   // nullable
        String passHash  = row.get("password_hash", String.class); // nullable

        if (idStr == null || userIdStr == null || provStr == null) {
            throw new IllegalStateException("Corrupt user_providers row: missing required fields");
        }

        return UserProvider.rehydrate(
                UserProviderId.of(UUID.fromString(idStr)),
                UserId.of(UUID.fromString(userIdStr)),
                AuthProvider.valueOf(provStr),
                extId,      // null for LOCAL
                passHash    // null for GOOGLE/GITHUB
        );
    }
}
