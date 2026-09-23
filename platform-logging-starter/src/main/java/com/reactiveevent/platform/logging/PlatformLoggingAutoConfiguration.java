package com.reactiveevent.platform.logging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class PlatformLoggingAutoConfiguration {

    @Bean
    public WebFilter traceIdLoggingFilter() {
        return (exchange, chain) -> {
            String traceId = java.util.UUID.randomUUID().toString();
            org.slf4j.MDC.put("traceId", traceId);

            return chain.filter(exchange)
                    .doFinally(signal -> org.slf4j.MDC.clear());
        };
    }
}
