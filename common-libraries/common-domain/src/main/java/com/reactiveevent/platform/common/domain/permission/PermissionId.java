package com.reactiveevent.platform.common.domain.permission;

import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public final class PermissionId extends ValueObject {

    @NonNull
    private final UUID value;

    private PermissionId(@NonNull UUID value) {
        this.value = value;
    }

    public static PermissionId of(@NonNull UUID value) {
        return new PermissionId(value);
    }

    public static PermissionId newId() {
        return new PermissionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
