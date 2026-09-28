package com.reactiveevent.platform.audit.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserLoggedInEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserLoggedInEventListener.class);

    private final ObjectMapper objectMapper;

    public UserLoggedInEventListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.user-logged-in}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) throws JsonProcessingException {
        UserLoggedInMessage event = objectMapper.readValue(message, UserLoggedInMessage.class);

        if (!"UserLoggedIn".equals(event.eventType()) || event.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported user login event type or schema version");
        }

        log.info(
                "Login audit event received: eventType={}, userId={}, provider={}, occurredAt={}",
                event.eventType(),
                event.userId(),
                event.provider(),
                event.occurredAt()
        );
    }

    @KafkaListener(
            topics = "${app.kafka.topics.login-failed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onLoginFailed(String message) throws JsonProcessingException {
        LoginFailedMessage event = objectMapper.readValue(message, LoginFailedMessage.class);

        if (!"LoginFailed".equals(event.eventType()) || event.schemaVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported login-failed event type or schema version"
            );
        }

        log.warn(
                "Login failure audit event received: eventType={}, provider={}, reason={}, occurredAt={}",
                event.eventType(),
                event.provider(),
                event.reason(),
                event.occurredAt()
        );
    }

    private record UserLoggedInMessage(
            String eventType,
            int schemaVersion,
            String userId,
            String provider,
            Instant occurredAt
    ) {}

    private record LoginFailedMessage(
            String eventType,
            int schemaVersion,
            String provider,
            String reason,
            Instant occurredAt
    ) {}
}
