package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.auth.application.events.LoginFailedEvent;
import reactor.core.publisher.Mono;

public interface LoginFailureEventPublisher {

    Mono<Void> publish(LoginFailedEvent event);
}
