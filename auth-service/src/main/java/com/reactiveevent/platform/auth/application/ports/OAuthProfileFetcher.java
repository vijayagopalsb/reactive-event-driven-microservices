package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.auth.OAuthProfile;
import reactor.core.publisher.Mono;

/**
 * OAuthProfileFetcher — application port for fetching a user's profile
 * from an OAuth2 provider using their access token.
 *
 * This is step 7 of the Authorization Code flow:
 *   After we have the access token, we call the provider's userinfo endpoint
 *   to get the user's identity: email, name, unique provider ID.
 *
 * Why separate from OAuthTokenExchanger?
 *   Single Responsibility: exchanging a code and fetching a profile are
 *   distinct operations that could fail independently.
 *   Separating them also makes each one independently testable.
 *
 * Returns: Mono<OAuthProfile> — normalized user data from the provider.
 *   The adapter translates provider-specific JSON into our OAuthProfile shape.
 *   The use case never sees raw JSON or provider-specific field names.
 */
public interface OAuthProfileFetcher {

    /**
     * Fetch the authenticated user's profile from the OAuth2 provider.
     *
     * @param accessToken the OAuth2 access token obtained from token exchange
     * @param provider    GOOGLE or GITHUB — determines which endpoint to call
     * @return Mono<OAuthProfile> normalized profile data
     *         Mono.error if the fetch fails (expired token, network error, etc.)
     */
    Mono<OAuthProfile> fetch(String accessToken, AuthProvider provider);
}
