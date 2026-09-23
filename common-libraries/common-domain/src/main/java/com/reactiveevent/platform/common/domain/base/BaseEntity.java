package com.reactiveevent.platform.common.domain.base;


import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;

@Getter
public abstract class BaseEntity<ID> {

    @NonNull
    protected final ID id;
    protected  final Instant createdAt;
    protected Instant updatedAt;

    protected BaseEntity(@NonNull ID id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    protected  void touch() {
        this.updatedAt = Instant.now();
    }

}