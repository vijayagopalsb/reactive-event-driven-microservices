package com.reactiveevent.platform.common.domain.auth;


import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Getter
@EqualsAndHashCode(callSuper = false)
public final class AuthToken extends ValueObject {

    @NonNull
    private final String value;

    private AuthToken(@NonNull String value) {
        this.value = value;
    }

    public static AuthToken of(@NonNull String value) {
        return new AuthToken(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
