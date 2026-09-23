package com.reactiveevent.platform.common.domain.user;

import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

/**
 * UserId — the unique identity of a User.
 *
 * Why wrap UUID in a class instead of using UUID directly?
 *
 * Type safety. Consider this method signature:
 *   assignRole(UUID userId, UUID roleId)    ← easy to swap args by mistake
 *   assignRole(UserId userId, RoleId roleId) ← compiler catches the swap
 *
 * This is a ValueObject — two UserIds are equal if their UUID values are equal.
 * ValueObject equality is by value, not by reference (no identity comparison).
 * That's why we use @EqualsAndHashCode and NOT the default Object.equals().
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public final class UserId extends ValueObject {

    @NonNull
    private final UUID value;

    private UserId(@NonNull UUID value) {
        this.value = value;
    }

    /** Use when loading a user from the database — UUID already exists. */
    public static UserId of(@NonNull UUID value) {
        return new UserId(value);
    }

    /** Use when creating a brand new user — generates a fresh random UUID. */
    public static UserId newId() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
