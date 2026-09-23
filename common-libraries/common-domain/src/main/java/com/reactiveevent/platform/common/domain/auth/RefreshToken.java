package com.reactiveevent.platform.common.domain.auth;



import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Getter
@EqualsAndHashCode(callSuper = false)
public final class RefreshToken extends ValueObject {

    @NonNull
    private final String value;

    private RefreshToken(@NonNull String value) {
        this.value = value;
    }

    public static RefreshToken of(@NonNull String value) {
        return new RefreshToken(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
