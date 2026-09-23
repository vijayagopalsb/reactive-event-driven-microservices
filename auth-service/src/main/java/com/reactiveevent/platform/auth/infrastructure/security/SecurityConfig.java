package com.reactiveevent.platform.auth.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SecurityConfig — Spring Security configuration for the auth-service.
 *
 * Two key annotations:
 *
 * @EnableWebFluxSecurity
 *   Activates Spring Security for reactive (WebFlux) applications.
 *   Without this, no security filter chain is registered.
 *
 * @EnableReactiveMethodSecurity
 *   Activates method-level security annotations like @PreAuthorize.
 *   Without this, @PreAuthorize("hasRole('ADMIN')") is silently ignored —
 *   every user can access every endpoint regardless of their role.
 *   This is the most common mistake when adding method security.
 *
 * JWT Authentication flow:
 *   1. Client sends: Authorization: Bearer eyJ...
 *   2. Spring extracts the token
 *   3. ReactiveJwtDecoder validates the signature using our RSA public key
 *   4. ReactiveJwtAuthenticationConverter converts JWT claims → Authentication object
 *   5. Our custom converter reads "roles" and "permissions" claims → GrantedAuthority list
 *   6. Spring Security stores the Authentication in the reactive SecurityContext
 *   7. @PreAuthorize checks the GrantedAuthority list against the required role
 *
 * Why RSA public key for verification (not the private key)?
 *   The private key SIGNS tokens (auth-service only).
 *   The public key VERIFIES tokens (auth-service + any downstream service).
 *   Anyone can verify with the public key — that's the point of asymmetric crypto.
 *   Sharing the public key is safe. Sharing the private key would be catastrophic.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity  // ← activates @PreAuthorize on controllers
public class SecurityConfig {

    // -------------------------------------------------------------------------
    // RSA Public Key → ReactiveJwtDecoder
    // Loads the public key from the Docker secret mount at startup.
    // Used to verify that every incoming JWT was signed by our auth-service.
    // -------------------------------------------------------------------------
    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${jwt.public-key:/run/secrets/jwt_public_key}") String publicKeyPath) {

        try {
            String pem = Files.readString(Path.of(publicKeyPath));

            pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(pem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey rsaPublicKey = (RSAPublicKey) keyFactory.generatePublic(keySpec);

            // NimbusJwtDecoder is blocking — wrap it in Mono.fromCallable
            // to avoid blocking the reactive event loop thread
            JwtDecoder blocking = NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
            return token -> Mono.fromCallable(() -> blocking.decode(token));

        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA public key from: " + publicKeyPath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Custom JWT → Authentication converter
    //
    // Problem: Spring Security's default JWT converter only maps the "scope"
    // claim to GrantedAuthority. Our JWT uses "roles" and "permissions" claims.
    //
    // Solution: a custom ReactiveJwtAuthenticationConverter that reads our
    // custom claims and converts them to GrantedAuthority objects.
    //
    // Without this:
    //   @PreAuthorize("hasRole('ADMIN')") → always fails → 403 for everyone
    //
    // With this:
    //   JWT claim "roles": ["ROLE_ADMIN"] → GrantedAuthority("ROLE_ADMIN")
    //   JWT claim "permissions": ["USER_READ"] → GrantedAuthority("USER_READ")
    //   @PreAuthorize("hasRole('ADMIN')") → checks for "ROLE_ADMIN" → passes ✅
    // -------------------------------------------------------------------------
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {

        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();

        // Override the default authority extraction with our custom logic
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            // Read "roles" claim → list of strings like ["ROLE_ADMIN", "ROLE_USER"]
            List<String> roles = jwt.getClaimAsStringList("roles");
            // Read "permissions" claim → list of strings like ["USER_READ", "USER_DELETE"]
            List<String> permissions = jwt.getClaimAsStringList("permissions");

            // Convert both lists to GrantedAuthority objects
            List<GrantedAuthority> authorities = new java.util.ArrayList<>();

            if (roles != null) {
                roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            if (permissions != null) {
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            // Return as Flux<GrantedAuthority> — the reactive converter expects this
            return Flux.fromIterable(authorities);
        });

        return converter;
    }

    // -------------------------------------------------------------------------
    // BCrypt password encoder
    // Used by: CreateUserUseCaseImpl (hashing), PasswordVerifierImpl (verifying)
    // Cost factor 10 = ~100ms per hash = brute force deterrent
    // -------------------------------------------------------------------------
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // -------------------------------------------------------------------------
    // Security filter chain — what needs a JWT and what doesn't
    // -------------------------------------------------------------------------
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            ReactiveJwtAuthenticationConverter jwtAuthenticationConverter) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // Why disable CSRF?
                // CSRF attacks exploit browser cookie-based sessions.
                // We use JWT in Authorization headers — not cookies.
                // Browser cannot be tricked into sending the Authorization header.
                // So CSRF protection is unnecessary and would only cause friction.

                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints — no JWT required
                        .pathMatchers("/auth/login").permitAll()
                        .pathMatchers("/auth/oauth2/**").permitAll()         // Phase 5
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Actuator management endpoints — require authentication
                        .pathMatchers("/actuator/**").authenticated()

                        // Everything else requires a valid JWT
                        // Method-level @PreAuthorize adds role checks on top of this
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwtSpec -> jwtSpec
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                        )
                )
                .build();
    }
}
