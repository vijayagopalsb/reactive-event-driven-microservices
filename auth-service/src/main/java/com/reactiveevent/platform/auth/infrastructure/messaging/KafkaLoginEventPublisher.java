package com.reactiveevent.platform.auth.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactiveevent.platform.auth.application.ports.LoginEventPublisher;
import com.reactiveevent.platform.common.domain.user.events.UserLoggedInEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KafkaLoginEventPublisher implements LoginEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaLoginEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.user-logged-in:auth.login-succeeded.v1}")
    private String topic;

    @Override
    public Mono<Void> publish(UserLoggedInEvent event) {
        return Mono.fromCallable(() -> {
                    LoginEventPayload payload = new LoginEventPayload(
                            "UserLoggedIn",
                            1,
                            event.getUserId().toString(),
                            event.getProvider().name(),
                            event.getOccurredAt()
                    );
                    return objectMapper.writeValueAsString(payload);
                })
                .flatMap(payload -> Mono.fromFuture(
                        kafkaTemplate.send(topic, event.getUserId().toString(), payload)
                ))
                .doOnSuccess(result -> log.debug(
                        "Published UserLoggedIn event for userId={} to topic={}",
                        event.getUserId(),
                        topic
                ))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
    }

    private record LoginEventPayload(
            String eventType,
            int schemaVersion,
            String userId,
            String provider,
            Instant occurredAt
    ) {}
}
