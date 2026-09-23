package com.reactiveevent.platform.common.domain.role;

import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public final class RoleId extends ValueObject {

    @NonNull
    private final UUID value;

    private RoleId(@NonNull UUID value) {

        this.value = value;
    }

    public static RoleId of(@NonNull UUID value) {

        return new RoleId(value);
    }

    public static RoleId newId() {

        return new RoleId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
