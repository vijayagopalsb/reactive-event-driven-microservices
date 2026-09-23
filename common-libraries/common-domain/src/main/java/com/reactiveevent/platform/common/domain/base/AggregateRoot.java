package com.reactiveevent.platform.common.domain.base;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public abstract class AggregateRoot<ID> extends BaseEntity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(ID id) {
        super(id);
    }

    protected void registerEvent(DomainEvent event) {

        this.domainEvents.add(event);
    }

    protected  void clearDomainEvents() {

        this.domainEvents.clear();
    }

    public  List<DomainEvent> getDomainEvents() {

        return Collections.unmodifiableList(domainEvents);
    }

}
