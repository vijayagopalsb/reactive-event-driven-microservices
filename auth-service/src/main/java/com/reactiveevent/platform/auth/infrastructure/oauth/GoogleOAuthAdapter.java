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

import java.util.Map;

/**
 * GoogleOAuthAdapter — implements both OAuthTokenExchanger and OAuthProfileFetcher
 * for the Google OAuth2 provider.
 *
 * This adapter handles:
 *   1. Token exchange: POST to Google's token endpoint with the authorization code
 *   2. Profile fetch:  GET to Google's userinfo endpoint with the access token
 *
 * Why one class for both interfaces?
 *   Both operations are Google-specific. Splitting them into two classes
 *   would just create two nearly empty classes that share the same config values.
 *   One adapter per provider is the clean pattern here.
 *
 * Why Spring WebClient?
 *   This is a reactive application. WebClient is Spring's non-blocking HTTP client.
 *   Using RestTemplate (blocking) would defeat the purpose of WebFlux.
 *
 * Google token endpoint:    POST https://oauth2.googleapis.com/token
 * Google userinfo endpoint: GET  https://www.googleapis.com/oauth2/v3/userinfo
 *
 * Google userinfo response:
 *   {
 *     "sub":   "109876543210",    ← unique Google user ID (externalId)
 *     "email": "john@gmail.com",
 *     "name":  "John Doe",
 *     "picture": "https://..."
 *   }
 */
@Slf4j
@Component
public class GoogleOAuthAdapter implements OAuthTokenExchanger, OAuthProfileFetcher {

    private final WebClient webClient;

    @Value("${oauth2.google.client-id}")
    private String clientId;

    @Value("${oauth2.google.client-secret}")
    private String clientSecret;

    @Value("${oauth2.google.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.google.token-uri}")
    private String tokenUri;

    @Value("${oauth2.google.userinfo-uri}")
    private String userInfoUri;

    // WebClient is injected — Spring Boot auto-configures a WebClient.Builder bean
    public GoogleOAuthAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // -------------------------------------------------------------------------
    // OAuthTokenExchanger — exchange the authorization code for an access token
    //
    // Google expects a POST with form-encoded body (not JSON):
    //   code=...&client_id=...&client_secret=...&redirect_uri=...&grant_type=authorization_code
    //
    // Response:
    //   { "access_token": "ya29...", "token_type": "Bearer", "expires_in": 3600 }
    // -------------------------------------------------------------------------
    @Override
    public Mono<String> exchange(String code, AuthProvider provider) {

        // Only handle GOOGLE — guard against wrong provider
        if (provider != AuthProvider.GOOGLE) {
            return Mono.error(new IllegalArgumentException(
                "GoogleOAuthAdapter cannot handle provider: " + provider
            ));
        }

        // Build form body — Google requires application/x-www-form-urlencoded
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code",          code);
        formData.add("client_id",     clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri",  redirectUri);
        formData.add("grant_type",    "authorization_code");

        log.debug("Exchanging Google authorization code for access token");

        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                // onStatus catches HTTP error responses (4xx, 5xx) and converts to errors
                .onStatus(status -> status.is4xxClientError(), response ->
                    Mono.error(new IllegalArgumentException(
                        "Google token exchange failed: invalid code or credentials"
                    ))
                )
                .onStatus(status -> status.is5xxServerError(), response ->
                    Mono.error(new RuntimeException(
                        "Google token exchange failed: server error"
                    ))
                )
                // Parse response as a Map — we only need "access_token"
                .bodyToMono(Map.class)
                .map(body -> {
                    String accessToken = (String) body.get("access_token");
                    if (accessToken == null) {
                        throw new IllegalStateException(
                            "Google token response missing access_token"
                        );
                    }
                    log.debug("Successfully obtained Google access token");
                    return accessToken;
                });
    }

    // -------------------------------------------------------------------------
    // OAuthProfileFetcher — fetch the user's profile using the access token
    //
    // Google expects: GET userinfo endpoint with Authorization: Bearer <token>
    //
    // Response:
    //   { "sub": "109876543210", "email": "john@gmail.com", "name": "John Doe" }
    // -------------------------------------------------------------------------
    @Override
    public Mono<OAuthProfile> fetch(String accessToken, AuthProvider provider) {

        if (provider != AuthProvider.GOOGLE) {
            return Mono.error(new IllegalArgumentException(
                "GoogleOAuthAdapter cannot handle provider: " + provider
            ));
        }

        log.debug("Fetching Google user profile");

        return webClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response ->
                    Mono.error(new IllegalArgumentException(
                        "Google profile fetch failed: invalid or expired access token"
                    ))
                )
                .onStatus(status -> status.is5xxServerError(), response ->
                    Mono.error(new RuntimeException(
                        "Google profile fetch failed: server error"
                    ))
                )
                .bodyToMono(Map.class)
                .map(body -> {
                    // "sub" = Google's unique user ID — never changes even if email changes
                    String sub   = (String) body.get("sub");
                    String email = (String) body.get("email");
                    String name  = (String) body.getOrDefault("name", email);

                    if (sub == null || email == null) {
                        throw new IllegalStateException(
                            "Google profile response missing required fields (sub, email)"
                        );
                    }

                    log.debug("Successfully fetched Google profile for sub: {}", sub);

                    return new OAuthProfile(
                            AuthProvider.GOOGLE,
                            sub,    // externalId = Google's "sub"
                            email,
                            name
                    );
                });
    }
}
