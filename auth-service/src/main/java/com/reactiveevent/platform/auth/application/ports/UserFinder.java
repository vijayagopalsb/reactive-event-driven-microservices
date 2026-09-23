package com.reactiveevent.platform.auth.application.ports;

import com.reactiveevent.platform.common.domain.user.User;
import reactor.core.publisher.Mono;

/**
 * UserFinder — application port for locating a user by email.
 *
 * This is a PORT — part of the hexagonal architecture.
 * Ports define what the application layer NEEDS from the outside world.
 * Adapters (infrastructure implementations) fulfill those needs.
 *
 * Why have this port if UserRepository already exists?
 *   UserRepository is a domain interface — it's a DDD concept.
 *   UserFinder is an application port — it's a hexagonal architecture concept.
 *   In this project, UserFinderImpl bridges the two:
 *     UserFinder (port) → UserFinderImpl (adapter) → UserRepository (domain interface)
 *
 * This layering means the LoginUseCaseImpl depends only on UserFinder.
 * It doesn't know that UserRepository or R2DBC exists.
 * This makes the use case testable with a simple mock of UserFinder.
 */
public interface UserFinder {

    /**
     * Find a user by their email address.
     * Returns Mono.empty() if no user exists — the caller handles the empty case.
     */
    Mono<User> findByEmail(String email);
}
