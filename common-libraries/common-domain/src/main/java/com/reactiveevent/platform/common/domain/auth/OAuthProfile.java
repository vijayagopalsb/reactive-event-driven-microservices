package com.reactiveevent.platform.common.domain.auth;

import lombok.NonNull;

/**
 * OAuthProfile — the user identity data returned by an OAuth2 provider.
 *
 * After your backend exchanges the authorization code for an access token
 * and fetches the user's profile from Google or GitHub, we normalize
 * the response into this single structure.
 *
 * Why normalize?
 *   Google returns: { "sub": "109876", "email": "...", "name": "..." }
 *   GitHub returns: { "id": 12345, "login": "...", "email": "...", "name": "..." }
 *   Different field names, different types for the ID.
 *
 *   OAuthProfile normalizes both into one shape so OAuthLoginUseCaseImpl
 *   doesn't need to know which provider it's dealing with.
 *   The adapter (Google/GitHub) does the translation — the use case sees
 *   only OAuthProfile.
 *
 * Fields:
 *   provider    → GOOGLE or GITHUB
 *   externalId  → the provider's unique user ID (Google "sub", GitHub numeric "id")
 *                 stored as String for consistency (GitHub id is a long)
 *   email       → the user's email from the provider
 *   name        → display name (used for future profile features)
 *
 * Note on GitHub email:
 *   GitHub allows users to make their email private.
 *   If email is private, the /user endpoint returns null.
 *   The adapter must call GET /user/emails as a fallback.
 *   We enforce @NonNull here so the adapter is forced to handle that case.
 *
 * This is a record — immutable, no behaviour, just data.
 * It is NOT persisted — it's a transient object that lives only during
 * the OAuth2 callback request processing.
 */
public record OAuthProfile(
        @NonNull AuthProvider provider,
        @NonNull String externalId,
        @NonNull String email,
        @NonNull String name
) {}
