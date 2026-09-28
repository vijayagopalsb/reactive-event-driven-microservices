package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.user.events.UserLoggedInEvent;
import reactor.core.publisher.Mono;

public interface LoginEventPublisher {

    Mono<Void> publish(UserLoggedInEvent event);
}
