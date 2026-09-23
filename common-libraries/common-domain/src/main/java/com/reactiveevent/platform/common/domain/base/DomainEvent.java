package com.reactiveevent.platform.common.domain.base;

import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;

@Getter
public abstract class DomainEvent {

    @NonNull
    private final Instant occurredAt;

    protected  DomainEvent() {
        this.occurredAt = Instant.now();
    }
}
