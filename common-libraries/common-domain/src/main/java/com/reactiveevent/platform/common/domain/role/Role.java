package com.reactiveevent.platform.common.domain.role;

import com.reactiveevent.platform.common.domain.base.AggregateRoot;
import lombok.Getter;
import lombok.NonNull;

@Getter
public final class Role extends AggregateRoot<RoleId> {

    @NonNull
    private final String name;

    private Role(@NonNull RoleId id,
                 @NonNull String name) {
        super(id);
        this.name = name;
    }

    public static Role createNew(@NonNull String name) {

        return new Role(RoleId.newId(), name);
    }

    public static Role rehydrate(@NonNull RoleId id,
                                 @NonNull String name) {
        return new Role(id, name);
    }

    @Override
    public String toString() {

        return "Role{id=" + getId() + ", name='" + name + "'}";
    }
}
