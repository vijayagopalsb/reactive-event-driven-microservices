package com.reactiveevent.platform.auth.application;

import com.reactiveevent.platform.auth.application.ports.OAuthProfileFetcher;
import com.reactiveevent.platform.auth.application.ports.OAuthTokenExchanger;
import com.reactiveevent.platform.auth.application.ports.TokenGenerator;
import com.reactiveevent.platform.auth.domain.repository.*;
import com.reactiveevent.platform.common.api.auth.LoginResult;
import com.reactiveevent.platform.common.api.auth.OAuthCallbackCommand;
import com.reactiveevent.platform.common.application.auth.OAuthLoginUseCase;
import com.reactiveevent.platform.common.domain.auth.AuthProvider;
import com.reactiveevent.platform.common.domain.auth.OAuthProfile;
import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuthLoginUseCaseImpl implements OAuthLoginUseCase {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginUseCaseImpl.class);

    // Injected as Maps to handle multiple implementations cleanly
    private final Map<String, OAuthTokenExchanger> tokenExchangers;
    private final Map<String, OAuthProfileFetcher> profileFetchers;

    private final UserProviderRepository userProviderRepository;
    private final WriteUserRepository    writeUserRepository;
    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final UserRoleRepository     userRoleRepository;
    private final PermissionRepository   permissionRepository;
    private final TokenGenerator         tokenGenerator;

    @Override
    public Mono<LoginResult> login(OAuthCallbackCommand command) {

        log.info("OAuth2 login attempt: provider={}", command.provider());

        // Derive the expected bean name (e.g., "googleOAuthAdapter" or "gitHubOAuthAdapter")
        String beanName = command.provider().name().toLowerCase() + "OAuthAdapter";

        OAuthTokenExchanger exchanger = tokenExchangers.get(beanName);
        OAuthProfileFetcher fetcher = profileFetchers.get(beanName);

        if (exchanger == null || fetcher == null) {
            return Mono.error(new IllegalArgumentException("No OAuth adapters found for provider: " + command.provider()));
        }

        // ── Steps 1 + 2: Exchange code → profile (sequential)
        return exchanger.exchange(command.code(), command.provider())
                .flatMap(accessToken ->
                        fetcher.fetch(accessToken, command.provider())
                )

                // ── Step 3: Find-or-Create ────────────────────────────────────
                .flatMap(profile ->
                        userProviderRepository
                                .findByProviderAndExternalId(
                                        profile.provider(),
                                        profile.externalId()
                                )
                                .flatMap(existingProvider -> {
                                    log.debug("Returning OAuth2 user: provider={}, externalId={}",
                                            profile.provider(), profile.externalId());

                                    return userRepository
                                            .findById(existingProvider.getUserId())
                                            .flatMap(user -> {
                                                if (!user.isActive()) {
                                                    return Mono.error(new IllegalStateException(
                                                            "Account is " + user.getStatus().name().toLowerCase()
                                                    ));
                                                }
                                                return issueToken(user, profile.provider());
                                            });
                                })
                                .switchIfEmpty(Mono.defer(() ->
                                        autoProvision(profile)
                                ))
                );
    }

    @Transactional
    protected Mono<LoginResult> autoProvision(OAuthProfile profile) {

        log.info("Auto-provisioning new OAuth2 user: email={}, provider={}",
                profile.email(), profile.provider());

        User newUser = User.createNew(profile.email());

        return writeUserRepository.save(newUser)
                .flatMap(savedUser -> {
                    UserProvider provider = UserProvider.createOAuth(
                            savedUser.getId(),
                            profile.provider(),
                            profile.externalId()
                    );

                    return userProviderRepository.save(provider)
                            .thenReturn(savedUser);
                })
                .flatMap(savedUser ->
                        roleRepository.findByName("USER")
                                .switchIfEmpty(Mono.error(new IllegalStateException(
                                        "Default USER role not found. Ensure seed data is applied."
                                )))
                                .flatMap(role ->
                                        userRoleRepository
                                                .assignRole(savedUser.getId(), role.getId())
                                                .thenReturn(savedUser)
                                )
                )
                .flatMap(savedUser -> issueToken(savedUser, profile.provider()));
    }

    private Mono<LoginResult> issueToken(User user, AuthProvider provider) {

        return Mono.zip(
                roleRepository.findRolesByUserId(user.getId()).collectList(),
                permissionRepository.findPermissionsByUserId(user.getId()).collectList()
        ).map(tuple -> {
            log.info("Issuing JWT for OAuth2 user: email={}, provider={}",
                    user.getEmail(), provider);

            return new LoginResult(
                    tokenGenerator.generateAccessToken(
                            user,
                            provider,
                            tuple.getT1(),
                            tuple.getT2()
                    ),
                    tokenGenerator.generateRefreshToken(user)
            );
        });
    }
}

//package com.reactiveevent.platform.auth.application;
//
//import com.reactiveevent.platform.auth.application.ports.OAuthProfileFetcher;
//import com.reactiveevent.platform.auth.application.ports.OAuthTokenExchanger;
//import com.reactiveevent.platform.auth.application.ports.TokenGenerator;
//import com.reactiveevent.platform.auth.domain.repository.*;
//import com.reactiveevent.platform.common.api.auth.LoginResult;
//import com.reactiveevent.platform.common.api.auth.OAuthCallbackCommand;
//import com.reactiveevent.platform.common.application.auth.OAuthLoginUseCase;
//import com.reactiveevent.platform.common.domain.auth.AuthProvider;
//import com.reactiveevent.platform.common.domain.auth.OAuthProfile;
//import com.reactiveevent.platform.common.domain.user.User;
//import com.reactiveevent.platform.common.domain.user.UserProvider;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import reactor.core.publisher.Mono;
//
//import java.util.Map;
//
///**
// * OAuthLoginUseCaseImpl — handles Google and GitHub OAuth2 login.
// *
// * Called when the user completes OAuth2 approval and Google/GitHub
// * redirects back to: GET /auth/oauth2/{provider}/callback?code=...
// *
// * The reactive chain:
// *
// *   Step 1: Exchange authorization code → OAuth2 access token
// *           (server-to-server call to Google/GitHub — user never sees this)
// *
// *   Step 2: Fetch user profile using access token
// *           → OAuthProfile { provider, externalId, email, name }
// *
// *   Step 3: Find-or-Create
// *           → Look up user_providers by (provider, externalId)
// *
// *           FOUND (returning user):
// *             → load User by userId from the provider row
// *             → check user is ACTIVE
// *             → skip to step 4
// *
// *           NOT FOUND (first login — auto-provision):
// *             → create User aggregate → save to users table
// *             → create UserProvider (OAuth2) → save to user_providers table
// *             → find USER role → assign to new user
// *             → all three writes in one @Transactional call
// *
// *   Step 4: Load roles + permissions in parallel (Mono.zip)
// *
// *   Step 5: Generate and return OUR JWT
// *           → { sub, email, provider, roles, permissions }
// *           → LoginResult { accessToken, refreshToken }
// */
//@Service
//@RequiredArgsConstructor
//public class OAuthLoginUseCaseImpl implements OAuthLoginUseCase {
//
//    private static final Logger log = LoggerFactory.getLogger(OAuthLoginUseCaseImpl.class);
//
//    // Injected as a Map so Spring automatically routes to "googleOAuthAdapter" or "gitHubOAuthAdapter"
//    private final Map<String, OAuthTokenExchanger> tokenExchangers;
//
//    private final OAuthProfileFetcher    profileFetcher;
//    private final UserProviderRepository userProviderRepository;
//    private final WriteUserRepository    writeUserRepository;
//    private final UserRepository         userRepository;
//    private final RoleRepository         roleRepository;
//    private final UserRoleRepository     userRoleRepository;
//    private final PermissionRepository   permissionRepository;
//    private final TokenGenerator         tokenGenerator;
//
//    @Override
//    public Mono<LoginResult> login(OAuthCallbackCommand command) {
//
//        log.info("OAuth2 login attempt: provider={}", command.provider());
//
//        // Dynamically resolve the correct bean based on provider name (e.g., "googleOAuthAdapter")
//        String beanName = command.provider().name().toLowerCase() + "OAuthAdapter";
//        OAuthTokenExchanger exchanger = tokenExchangers.get(beanName);
//
//        if (exchanger == null) {
//            return Mono.error(new IllegalArgumentException("No OAuth token exchanger found for provider: " + command.provider()));
//        }
//
//        // ── Steps 1 + 2: Exchange code → profile (sequential — each depends on previous)
//        return exchanger.exchange(command.code(), command.provider())
//                .flatMap(accessToken ->
//                        profileFetcher.fetch(accessToken, command.provider())
//                )
//
//                // ── Step 3: Find-or-Create ────────────────────────────────────
//                .flatMap(profile ->
//                        userProviderRepository
//                                .findByProviderAndExternalId(
//                                        profile.provider(),
//                                        profile.externalId()
//                                )
//                                .flatMap(existingProvider -> {
//                                    // ── FOUND: returning user ─────────────────
//                                    log.debug("Returning OAuth2 user: provider={}, externalId={}",
//                                            profile.provider(), profile.externalId());
//
//                                    return userRepository
//                                            .findById(existingProvider.getUserId())
//                                            .flatMap(user -> {
//                                                if (!user.isActive()) {
//                                                    return Mono.error(new IllegalStateException(
//                                                            "Account is " + user.getStatus().name().toLowerCase()
//                                                    ));
//                                                }
//                                                return issueToken(user, profile.provider());
//                                            });
//                                })
//                                // switchIfEmpty fires when findByProviderAndExternalId
//                                // returns Mono.empty() = first login ever for this account
//                                .switchIfEmpty(Mono.defer(() ->
//                                        autoProvision(profile)
//                                ))
//                );
//    }
//
//    // -------------------------------------------------------------------------
//    // autoProvision — create a brand new user on first OAuth2 login
//    // -------------------------------------------------------------------------
//    @Transactional
//    protected Mono<LoginResult> autoProvision(OAuthProfile profile) {
//
//        log.info("Auto-provisioning new OAuth2 user: email={}, provider={}",
//                profile.email(), profile.provider());
//
//        // Create User aggregate
//        User newUser = User.createNew(profile.email());
//
//        return writeUserRepository.save(newUser)
//                .flatMap(savedUser -> {
//
//                    // Create UserProvider (OAuth2 — no password)
//                    UserProvider provider = UserProvider.createOAuth(
//                            savedUser.getId(),
//                            profile.provider(),
//                            profile.externalId()
//                    );
//
//                    return userProviderRepository.save(provider)
//                            .thenReturn(savedUser);
//                })
//                .flatMap(savedUser ->
//                        // Assign default USER role to all OAuth2 self-onboarded users
//                        roleRepository.findByName("USER")
//                                .switchIfEmpty(Mono.error(new IllegalStateException(
//                                        "Default USER role not found. Ensure seed data is applied."
//                                )))
//                                .flatMap(role ->
//                                        userRoleRepository
//                                                .assignRole(savedUser.getId(), role.getId())
//                                                .thenReturn(savedUser)
//                                )
//                )
//                .flatMap(savedUser -> issueToken(savedUser, profile.provider()));
//    }
//
//    // -------------------------------------------------------------------------
//    // issueToken — shared by both returning user and auto-provisioned user paths
//    // -------------------------------------------------------------------------
//    private Mono<LoginResult> issueToken(User user, AuthProvider provider) {
//
//        return Mono.zip(
//                roleRepository.findRolesByUserId(user.getId()).collectList(),
//                permissionRepository.findPermissionsByUserId(user.getId()).collectList()
//        ).map(tuple -> {
//            log.info("Issuing JWT for OAuth2 user: email={}, provider={}",
//                    user.getEmail(), provider);
//
//            return new LoginResult(
//                    tokenGenerator.generateAccessToken(
//                            user,
//                            provider,
//                            tuple.getT1(),
//                            tuple.getT2()
//                    ),
//                    tokenGenerator.generateRefreshToken(user)
//            );
//        });
//    }
//}

//package com.reactiveevent.platform.auth.application;
//
//import com.reactiveevent.platform.auth.application.ports.OAuthProfileFetcher;
//import com.reactiveevent.platform.auth.application.ports.OAuthTokenExchanger;
//import com.reactiveevent.platform.auth.application.ports.TokenGenerator;
//import com.reactiveevent.platform.auth.domain.repository.*;
//import com.reactiveevent.platform.common.api.auth.LoginResult;
//import com.reactiveevent.platform.common.api.auth.OAuthCallbackCommand;
//import com.reactiveevent.platform.common.application.auth.OAuthLoginUseCase;
//import com.reactiveevent.platform.common.domain.auth.AuthProvider;
//import com.reactiveevent.platform.common.domain.auth.OAuthProfile;
//import com.reactiveevent.platform.common.domain.user.User;
//import com.reactiveevent.platform.common.domain.user.UserProvider;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import reactor.core.publisher.Mono;
//
///**
// * OAuthLoginUseCaseImpl — handles Google and GitHub OAuth2 login.
// *
// * Called when the user completes OAuth2 approval and Google/GitHub
// * redirects back to: GET /auth/oauth2/{provider}/callback?code=...
// *
// * The reactive chain:
// *
// *   Step 1: Exchange authorization code → OAuth2 access token
// *           (server-to-server call to Google/GitHub — user never sees this)
// *
// *   Step 2: Fetch user profile using access token
// *           → OAuthProfile { provider, externalId, email, name }
// *
// *   Step 3: Find-or-Create
// *           → Look up user_providers by (provider, externalId)
// *
// *           FOUND (returning user):
// *             → load User by userId from the provider row
// *             → check user is ACTIVE
// *             → skip to step 4
// *
// *           NOT FOUND (first login — auto-provision):
// *             → create User aggregate → save to users table
// *             → create UserProvider (OAuth2) → save to user_providers table
// *             → find USER role → assign to new user
// *             → all three writes in one @Transactional call
// *
// *   Step 4: Load roles + permissions in parallel (Mono.zip)
// *
// *   Step 5: Generate and return OUR JWT
// *           → { sub, email, provider, roles, permissions }
// *           → LoginResult { accessToken, refreshToken }
// *
// * Key insight — after step 5, Google/GitHub is out of the picture.
// * The OAuth2 access token is discarded. We never store it.
// * The user's browser now uses YOUR JWT for all subsequent requests.
// */
//@Service
//@RequiredArgsConstructor
//public class OAuthLoginUseCaseImpl implements OAuthLoginUseCase {
//
//    private static final Logger log = LoggerFactory.getLogger(OAuthLoginUseCaseImpl.class);
//
//    private final OAuthTokenExchanger    tokenExchanger;
//    private final OAuthProfileFetcher    profileFetcher;
//    private final UserProviderRepository userProviderRepository;
//    private final WriteUserRepository    writeUserRepository;
//    private final UserRepository         userRepository;
//    private final RoleRepository         roleRepository;
//    private final UserRoleRepository     userRoleRepository;
//    private final PermissionRepository   permissionRepository;
//    private final TokenGenerator         tokenGenerator;
//
//    @Override
//    public Mono<LoginResult> login(OAuthCallbackCommand command) {
//
//        log.info("OAuth2 login attempt: provider={}", command.provider());
//
//        // ── Steps 1 + 2: Exchange code → profile (sequential — each depends on previous)
//        return tokenExchanger.exchange(command.code(), command.provider())
//                .flatMap(accessToken ->
//                        profileFetcher.fetch(accessToken, command.provider())
//                )
//
//                // ── Step 3: Find-or-Create ────────────────────────────────────
//                .flatMap(profile ->
//                        userProviderRepository
//                                .findByProviderAndExternalId(
//                                        profile.provider(),
//                                        profile.externalId()
//                                )
//                                .flatMap(existingProvider -> {
//                                    // ── FOUND: returning user ─────────────────
//                                    log.debug("Returning OAuth2 user: provider={}, externalId={}",
//                                            profile.provider(), profile.externalId());
//
//                                    return userRepository
//                                            .findById(existingProvider.getUserId())
//                                            .flatMap(user -> {
//                                                if (!user.isActive()) {
//                                                    return Mono.error(new IllegalStateException(
//                                                        "Account is " + user.getStatus().name().toLowerCase()
//                                                    ));
//                                                }
//                                                return issueToken(user, profile.provider());
//                                            });
//                                })
//                                // switchIfEmpty fires when findByProviderAndExternalId
//                                // returns Mono.empty() = first login ever for this account
//                                .switchIfEmpty(Mono.defer(() ->
//                                        autoProvision(profile)
//                                ))
//                );
//    }
//
//    // -------------------------------------------------------------------------
//    // autoProvision — create a brand new user on first OAuth2 login
//    //
//    // Three writes wrapped in one @Transactional:
//    //   1. INSERT INTO users
//    //   2. INSERT INTO user_providers
//    //   3. INSERT INTO user_roles (USER role by default)
//    // -------------------------------------------------------------------------
//    @Transactional
//    protected Mono<LoginResult> autoProvision(OAuthProfile profile) {
//
//        log.info("Auto-provisioning new OAuth2 user: email={}, provider={}",
//                profile.email(), profile.provider());
//
//        // Create User aggregate
//        User newUser = User.createNew(profile.email());
//
//        return writeUserRepository.save(newUser)
//                .flatMap(savedUser -> {
//
//                    // Create UserProvider (OAuth2 — no password)
//                    UserProvider provider = UserProvider.createOAuth(
//                            savedUser.getId(),
//                            profile.provider(),
//                            profile.externalId()
//                    );
//
//                    return userProviderRepository.save(provider)
//                            .thenReturn(savedUser);
//                })
//                .flatMap(savedUser ->
//                    // Assign default USER role to all OAuth2 self-onboarded users
//                    // Admin can promote them later via POST /users/{id}/roles
//                    roleRepository.findByName("USER")
//                            .switchIfEmpty(Mono.error(new IllegalStateException(
//                                "Default USER role not found. Ensure seed data is applied."
//                            )))
//                            .flatMap(role ->
//                                userRoleRepository
//                                        .assignRole(savedUser.getId(), role.getId())
//                                        .thenReturn(savedUser)
//                            )
//                )
//                .flatMap(savedUser -> issueToken(savedUser, profile.provider()));
//    }
//
//    // -------------------------------------------------------------------------
//    // issueToken — shared by both returning user and auto-provisioned user paths
//    // Loads roles + permissions in parallel, then generates JWT
//    // -------------------------------------------------------------------------
//    private Mono<LoginResult> issueToken(User user, AuthProvider provider) {
//
//        return Mono.zip(
//                roleRepository.findRolesByUserId(user.getId()).collectList(),
//                permissionRepository.findPermissionsByUserId(user.getId()).collectList()
//        ).map(tuple -> {
//            log.info("Issuing JWT for OAuth2 user: email={}, provider={}",
//                    user.getEmail(), provider);
//
//            return new LoginResult(
//                    tokenGenerator.generateAccessToken(
//                            user,
//                            provider,
//                            tuple.getT1(),
//                            tuple.getT2()
//                    ),
//                    tokenGenerator.generateRefreshToken(user)
//            );
//        });
//    }
//}
