package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import com.reactiveevent.platform.common.domain.user.UserId;
import reactor.core.publisher.Mono;

/**
 * UserProviderRepository — read and write operations on the user_providers table.
 *
 * This interface covers BOTH reads and writes for user_providers.
 * Unlike users (where we split read/write), user_providers are always
 * read and written together in the same use cases (login, create user),
 * so splitting them would add complexity without benefit.
 *
 * Three query patterns we need:
 *
 * 1. findByEmailAndProvider(email, LOCAL)
 *    → Used during LOCAL login.
 *      We have the user's email from the LoginCommand.
 *      We need to find the user_providers row for LOCAL to get the password hash.
 *      This joins users + user_providers in one query.
 *
 * 2. findByProviderAndExternalId(GOOGLE, "109876543210")
 *    → Used during OAuth2 login (find-or-create step).
 *      We have the provider's unique user ID (Google sub, GitHub id).
 *      We look for an existing user_providers row by (provider, external_id).
 *      If found → returning user. If empty → first login, auto-provision.
 *
 * 3. save(UserProvider)
 *    → Used when creating a new LOCAL user (admin POST /users)
 *      and when auto-provisioning an OAuth2 user (first Google/GitHub login).
 */
public interface UserProviderRepository {

    /**
     * Find the provider row by email + provider type.
     * Joins the users table to match by email.
     * Returns Mono.empty() if no match — caller treats this as "invalid credentials".
     *
     * Example: findByEmailAndProvider("admin@example.com", LOCAL)
     */
    Mono<UserProvider> findByEmailAndProvider(String email, AuthProvider provider);

    /**
     * Find the provider row by provider type + the external OAuth2 user ID.
     * Used for OAuth2 login find-or-create.
     * Returns Mono.empty() if this OAuth2 account has never logged in before.
     *
     * Example: findByProviderAndExternalId(GOOGLE, "109876543210")
     */
    Mono<UserProvider> findByProviderAndExternalId(AuthProvider provider, String externalId);

    /**
     * Find a provider row by userId + provider type.
     * Used when we already have the UserId and want the specific provider.
     *
     * Example: findByUserIdAndProvider(userId, LOCAL) → to check if user has a password
     */
    Mono<UserProvider> findByUserIdAndProvider(UserId userId, AuthProvider provider);

    /**
     * Persist a new UserProvider row.
     * Called when:
     *   - Admin creates a LOCAL user (saves the password hash row)
     *   - OAuth2 user logs in for the first time (saves the externalId row)
     */
    Mono<UserProvider> save(UserProvider userProvider);
}
