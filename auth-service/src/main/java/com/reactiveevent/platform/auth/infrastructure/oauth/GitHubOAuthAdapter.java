package com.reactiveevent.platform.auth.infrastructure.oauth;

import com.reactiveevent.platform.auth.application.ports.OAuthProfileFetcher;
import com.reactiveevent.platform.auth.application.ports.OAuthTokenExchanger;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.auth.OAuthProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * GitHubOAuthAdapter — implements OAuthTokenExchanger and OAuthProfileFetcher
 * for the GitHub OAuth2 provider.
 *
 * GitHub has two important differences from Google:
 *
 * 1. Token exchange response format:
 *    By default GitHub returns form-encoded text: access_token=gho_...&token_type=bearer
 *    We request JSON by sending Accept: application/json
 *
 * 2. Email privacy:
 *    GitHub users can mark their email as private.
 *    When private, GET /user returns null for the email field.
 *    We MUST call GET /user/emails as a fallback to find their primary email.
 *    This is a well-known gotcha in GitHub OAuth2 integration.
 *
 * GitHub token endpoint:    POST https://github.com/login/oauth/access_token
 * GitHub user endpoint:     GET  https://api.github.com/user
 * GitHub emails endpoint:   GET  https://api.github.com/user/emails (fallback)
 *
 * GitHub /user response:
 *   {
 *     "id":    12345678,          ← numeric user ID (our externalId)
 *     "login": "johndoe",
 *     "email": "john@example.com" OR null (if private)
 *     "name":  "John Doe"
 *   }
 *
 * GitHub /user/emails response (when email is private):
 *   [
 *     { "email": "john@example.com", "primary": true, "verified": true },
 *     { "email": "other@example.com", "primary": false, "verified": true }
 *   ]
 */
@Slf4j
@Component
public class GitHubOAuthAdapter implements OAuthTokenExchanger, OAuthProfileFetcher {

    private final WebClient webClient;

    @Value("${oauth2.github.client-id}")
    private String clientId;

    @Value("${oauth2.github.client-secret}")
    private String clientSecret;

    @Value("${oauth2.github.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.github.token-uri}")
    private String tokenUri;

    @Value("${oauth2.github.userinfo-uri}")
    private String userInfoUri;

    @Value("${oauth2.github.emails-uri}")
    private String emailsUri;

    public GitHubOAuthAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // -------------------------------------------------------------------------
    // OAuthTokenExchanger — exchange the authorization code for an access token
    //
    // GitHub requires: Accept: application/json header to get JSON response
    // Without it, GitHub returns URL-encoded text: access_token=gho_...
    // -------------------------------------------------------------------------
    @Override
    public Mono<String> exchange(String code, AuthProvider provider) {

        if (provider != AuthProvider.GITHUB) {
            return Mono.error(new IllegalArgumentException(
                "GitHubOAuthAdapter cannot handle provider: " + provider
            ));
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code",          code);
        formData.add("client_id",     clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri",  redirectUri);

        log.debug("Exchanging GitHub authorization code for access token");

        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                // Tell GitHub we want JSON — without this, response is form-encoded text
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                    Mono.error(new IllegalArgumentException(
                        "GitHub token exchange failed: invalid code or credentials"
                    ))
                )
                .onStatus(status -> status.is5xxServerError(), response ->
                    Mono.error(new RuntimeException(
                        "GitHub token exchange failed: server error"
                    ))
                )
                .bodyToMono(Map.class)
                .map(body -> {
                    String accessToken = (String) body.get("access_token");
                    if (accessToken == null) {
                        throw new IllegalStateException(
                            "GitHub token response missing access_token"
                        );
                    }
                    log.debug("Successfully obtained GitHub access token");
                    return accessToken;
                });
    }

    // -------------------------------------------------------------------------
    // OAuthProfileFetcher — fetch user profile, with email fallback
    //
    // Step A: GET /user → get id, login, name, email (may be null)
    // Step B: if email is null → GET /user/emails → find primary verified email
    // -------------------------------------------------------------------------
    @Override
    public Mono<OAuthProfile> fetch(String accessToken, AuthProvider provider) {

        if (provider != AuthProvider.GITHUB) {
            return Mono.error(new IllegalArgumentException(
                "GitHubOAuthAdapter cannot handle provider: " + provider
            ));
        }

        log.debug("Fetching GitHub user profile");

        // Step A: fetch main user profile
        return webClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                    Mono.error(new IllegalArgumentException(
                        "GitHub profile fetch failed: invalid or expired access token"
                    ))
                )
                .bodyToMono(Map.class)
                .flatMap(body -> {

                    // GitHub ID is a numeric integer — convert to String for our externalId
                    Object idObj = body.get("id");
                    if (idObj == null) {
                        return Mono.error(new IllegalStateException(
                            "GitHub profile response missing 'id' field"
                        ));
                    }
                    String externalId = String.valueOf(idObj);
                    String email      = (String) body.get("email");  // may be null
                    String name       = (String) body.getOrDefault("name",
                                            body.getOrDefault("login", "GitHub User"));

                    // Step B: if email is null, fetch from /user/emails
                    if (email == null || email.isBlank()) {
                        log.debug("GitHub email is private, fetching from /user/emails");
                        return fetchPrimaryEmail(accessToken)
                                .map(primaryEmail -> new OAuthProfile(
                                        AuthProvider.GITHUB,
                                        externalId,
                                        primaryEmail,
                                        (String) name
                                ));
                    }

                    return Mono.just(new OAuthProfile(
                            AuthProvider.GITHUB,
                            externalId,
                            email,
                            (String) name
                    ));
                });
    }

    // -------------------------------------------------------------------------
    // fetchPrimaryEmail — fallback for GitHub users with private email
    // Calls GET /user/emails and finds the primary + verified email
    // -------------------------------------------------------------------------
    private Mono<String> fetchPrimaryEmail(String accessToken) {
        return webClient.get()
                .uri(emailsUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToFlux(Map.class)
                .filter(emailObj -> {
                    // Find the email that is both primary AND verified
                    Boolean primary  = (Boolean) emailObj.get("primary");
                    Boolean verified = (Boolean) emailObj.get("verified");
                    return Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified);
                })
                .next()  // take the first matching email
                .map(emailObj -> (String) emailObj.get("email"))
                .switchIfEmpty(Mono.error(new IllegalStateException(
                    "GitHub account has no primary verified email address. "
                    + "Please add a verified email to your GitHub account."
                )));
    }
}
