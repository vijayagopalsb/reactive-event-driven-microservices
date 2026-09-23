package com.reactiveevent.platform.common.domain.permission;

import com.reactiveevent.platform.common.domain.base.AggregateRoot;
import lombok.Getter;
import lombok.NonNull;

@Getter
public final class Permission extends AggregateRoot<PermissionId> {

    @NonNull
    private final String name;

    private Permission(@NonNull PermissionId id,
                       @NonNull String name) {
        super(id);
        this.name = name;
    }

    public static Permission createNew(@NonNull String name) {

        return new Permission(PermissionId.newId(), name);
    }

    public static Permission rehydrate(@NonNull PermissionId id,
                                       @NonNull String name) {
        return new Permission(id, name);
    }

    @Override
    public String toString() {

        return "Permission{id=" + getId() + ", name='" + name + "'}";
    }
}
