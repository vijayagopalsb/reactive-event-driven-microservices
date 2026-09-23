package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import reactor.core.publisher.Mono;

/**
 * OAuthTokenExchanger — application port for exchanging an authorization code
 * for an OAuth2 access token.
 *
 * This is step 6 of the Authorization Code flow:
 *   The authorization code (from the callback URL) is short-lived and single-use.
 *   We exchange it server-to-server with Google/GitHub for an access token.
 *   The access token is then used to fetch the user's profile.
 *
 * Why a port?
 *   The actual HTTP call to Google/GitHub is an infrastructure concern.
 *   The use case should not know about WebClient, HTTP status codes,
 *   or provider-specific JSON response shapes.
 *   The port defines WHAT we need. The adapters define HOW we get it.
 *
 * Returns: Mono<String> — the OAuth2 access token as a raw string.
 *   We don't model it as a value object because it's temporary —
 *   used immediately to fetch the profile, then discarded.
 */
public interface OAuthTokenExchanger {

    /**
     * Exchange an authorization code for an OAuth2 access token.
     *
     * @param code        the authorization code from the callback URL query param
     * @param provider    GOOGLE or GITHUB — determines which endpoint to call
     * @return Mono<String> the OAuth2 access token from the provider
     *         Mono.error if the exchange fails (invalid code, network error, etc.)
     */
    Mono<String> exchange(String code, AuthProvider provider);
}
