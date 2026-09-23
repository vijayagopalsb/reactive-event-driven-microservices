package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.auth.AuthToken;
import com.reactiveevent.platform.common.domain.auth.RefreshToken;
import com.reactiveevent.platform.common.domain.permission.Permission;
import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.user.User;

import java.util.List;

/**
 * TokenGenerator — application port for JWT generation.
 *
 * Why does generateAccessToken now take roles, permissions, and provider?
 *
 * The access token is a self-contained identity document.
 * Downstream services (other microservices behind the API gateway) validate
 * the JWT and read claims directly — they never call the auth-service.
 * So the token must carry everything those services need to make decisions:
 *
 *   roles       → "is this user an ADMIN?" — coarse-grained access control
 *   permissions → "can this user DELETE users?" — fine-grained access control
 *   provider    → "did they log in via Google?" — for audit and conditional logic
 *
 * Why not add roles/permissions to the refresh token?
 *   The refresh token's only job is to get a new access token.
 *   It doesn't need claims — it just needs to identify the user (sub).
 *   Keeping it minimal is a security choice: less data in a long-lived token
 *   means less damage if it's ever leaked or stolen.
 */
public interface TokenGenerator {

    /**
     * Generate a short-lived access token (15 minutes).
     * Contains: sub, email, provider, roles, permissions, iss, iat, exp
     */
    AuthToken generateAccessToken(User user,
                                  AuthProvider provider,
                                  List<Role> roles,
                                  List<Permission> permissions);

    /**
     * Generate a long-lived refresh token (7 days).
     * Contains: sub, iss, iat, exp — nothing else.
     * Used only to obtain a new access token, not for authorization decisions.
     */
    RefreshToken generateRefreshToken(User user);
}
