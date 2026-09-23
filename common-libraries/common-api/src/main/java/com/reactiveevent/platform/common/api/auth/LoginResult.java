package com.reactiveevent.platform.common.api.auth;


import com.reactiveevent.platform.common.domain.auth.AuthToken;
import com.reactiveevent.platform.common.domain.auth.RefreshToken;

public record LoginResult(
        AuthToken accessToken,
        RefreshToken refreshToken
) {}
