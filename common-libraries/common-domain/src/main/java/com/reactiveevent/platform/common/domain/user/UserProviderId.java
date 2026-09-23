package com.reactiveevent.platform.common.domain.user;

import com.reactiveevent.platform.common.domain.base.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

/**
 * UserProviderId — the unique identity of a UserProvider row.
 *
 * Follows the same pattern as UserId:
 *   of(uuid)  → used when loading from the database (UUID already exists)
 *   newId()   → used when creating a new provider entry (generates fresh UUID)
 *
 * Why a separate ID type and not just UserId?
 * Because UserProviderId and UserId are different things.
 * A user_providers row has its OWN primary key — separate from the user's ID.
 * Using the same type would allow accidentally passing a UserId where a
 * UserProviderId is expected, which would be a silent bug.
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public final class UserProviderId extends ValueObject {

    @NonNull
    private final UUID value;

    private UserProviderId(@NonNull UUID value) {
        this.value = value;
    }

    /** Use when loading a provider row from the database. */
    public static UserProviderId of(@NonNull UUID value) {
        return new UserProviderId(value);
    }

    /** Use when creating a new provider entry. */
    public static UserProviderId newId() {
        return new UserProviderId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
