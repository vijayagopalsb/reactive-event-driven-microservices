//package com.reactiveevent.platform.gateway.security;
//
//import org.springframework.cloud.gateway.route.RouteLocator;
//import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class GatewayConfig {
//
//    @Bean
//    public RouteLocator customRoutes(RouteLocatorBuilder builder, JwtAuthenticationFilter jwtFilter) {
//        return builder.routes()
//                .route("auth_route", r -> r.path("/auth/**")
//                        .filters(f -> f.filter(jwtFilter))
//                        .uri("lb://AUTH-SERVICE"))
//                .build();
//    }
//}
