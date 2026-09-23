package com.reactiveevent.platform.auth.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reactiveevent.platform.auth.application.ports.TokenGenerator;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.auth.AuthToken;
import com.reactiveevent.platform.common.domain.auth.RefreshToken;
import com.reactiveevent.platform.common.domain.permission.Permission;
import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.user.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * TokenGeneratorImpl — signs JWTs using RSA private key (RS256 algorithm).
 *
 * Why RSA (asymmetric) instead of HMAC (symmetric)?
 *   HMAC (HS256): one secret key for both signing AND verifying.
 *     → Every service that needs to verify tokens must know the secret.
 *     → If any service is compromised, the secret is exposed.
 *
 *   RSA (RS256): private key signs, public key verifies.
 *     → Only the auth-service knows the private key.
 *     → All other services only need the public key (safe to distribute).
 *     → A compromised downstream service cannot forge tokens.
 *
 * Key loading:
 *   The private key is mounted as a Docker secret (/run/secrets/jwt_private_key).
 *   @PostConstruct loads it once at startup — not on every token generation.
 *   This is efficient and avoids repeated file I/O per request.
 *
 * JWT claims in the access token:
 *   sub         → user's UUID (standard JWT subject claim)
 *   email       → user's email
 *   provider    → LOCAL / GOOGLE / GITHUB
 *   roles       → list of role names e.g. ["ROLE_ADMIN"]
 *   permissions → list of permission names e.g. ["USER_READ", "USER_DELETE"]
 *   iss         → "auth-service" (who issued this token)
 *   iat         → issued-at timestamp
 *   exp         → expiration timestamp (15 minutes from now)
 */
@Component
public class TokenGeneratorImpl implements TokenGenerator {

    @Value("${jwt.private-key}")
    private String privateKeyPath;

    @Value("${jwt.issuer:auth-service}")
    private String issuer;

    @Value("${jwt.access-token-expiry:900}")
    private long accessTokenExpirySeconds;

    @Value("${jwt.refresh-token-expiry:604800}")
    private long refreshTokenExpirySeconds;

    private RSAPrivateKey privateKey;

    // -------------------------------------------------------------------------
    // @PostConstruct — runs once after Spring creates this bean
    // Loads the RSA private key from the file system into memory.
    // We load it once and reuse it for every token generation.
    // -------------------------------------------------------------------------
    @PostConstruct
    public void init() throws Exception {
        String pem = Files.readString(Path.of(privateKeyPath));

        // Strip PEM headers and whitespace to get the raw Base64 content
        String keyContent = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decodedKey = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.privateKey = (RSAPrivateKey) kf.generatePrivate(spec);
    }

    // -------------------------------------------------------------------------
    // generateAccessToken — short-lived, carries full identity + authorization
    // -------------------------------------------------------------------------
    @Override
    public AuthToken generateAccessToken(User user,
                                         AuthProvider provider,
                                         List<Role> roles,
                                         List<Permission> permissions) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);

            // Extract role names and permission names as plain strings for the JWT
            // JWT claims are JSON — we store them as string arrays
            List<String> roleNames = roles.stream()
                    .map(role -> "ROLE_" + role.getName())  // Spring Security convention: prefix with ROLE_
                    .toList();

            List<String> permissionNames = permissions.stream()
                    .map(Permission::getName)
                    .toList();

            long now = System.currentTimeMillis();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().getValue().toString())       // user UUID
                    .claim("email",       user.getEmail())             // email
                    .claim("provider",    provider.name())             // LOCAL/GOOGLE/GITHUB
                    .claim("roles",       roleNames)                   // ["ROLE_ADMIN"]
                    .claim("permissions", permissionNames)             // ["USER_READ", "USER_DELETE"]
                    .issuer(issuer)                                    // "auth-service"
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + accessTokenExpirySeconds * 1000))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
            );
            signedJWT.sign(signer);

            return AuthToken.of(signedJWT.serialize());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    // -------------------------------------------------------------------------
    // generateRefreshToken — long-lived, minimal claims
    // Only contains subject (userId) and expiry — nothing sensitive
    // -------------------------------------------------------------------------
    @Override
    public RefreshToken generateRefreshToken(User user) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);

            long now = System.currentTimeMillis();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().getValue().toString())
                    .issuer(issuer)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + refreshTokenExpirySeconds * 1000))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claims
            );
            signedJWT.sign(signer);

            return RefreshToken.of(signedJWT.serialize());

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate refresh token", e);
        }
    }
}
