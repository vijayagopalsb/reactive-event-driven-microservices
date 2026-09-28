package com.reactiveevent.platform.auth.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactiveevent.platform.auth.application.events.LoginFailedEvent;
import com.reactiveevent.platform.auth.application.ports.LoginFailureEventPublisher;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class KafkaLoginFailureEventPublisher implements LoginFailureEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaLoginFailureEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    @Value("${app.kafka.topics.login-failed:auth.login-failed.v1}")
    private String topic;


    @Override
    public Mono<Void> publish(LoginFailedEvent event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .flatMap(message ->
                        Mono.fromFuture(kafkaTemplate.send(topic, message)))
                .doOnSuccess(result ->
                        log.debug("Published login-failed audit event to topic={}", topic))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
    }
}
