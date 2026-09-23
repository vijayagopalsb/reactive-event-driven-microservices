package com.reactiveevent.platform.auth.infrastructure.user;

import com.reactiveevent.platform.auth.application.ports.UserFinder;
import com.reactiveevent.platform.auth.domain.repository.UserRepository;
import com.reactiveevent.platform.common.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * UserFinderImpl — adapter that connects the UserFinder port to the UserRepository.
 *
 * In hexagonal architecture:
 *   PORT    = UserFinder (what the application layer asks for)
 *   ADAPTER = UserFinderImpl (how the infrastructure fulfills that request)
 *
 * Why not inject UserRepository directly into LoginUseCaseImpl?
 *   The use case would then depend on a domain repository interface,
 *   which is still an infrastructure-adjacent concept.
 *   The UserFinder port is a thinner, more focused abstraction — it only
 *   exposes what the login use case needs (findByEmail), nothing more.
 *   This makes the use case easier to test and reason about in isolation.
 */
@Component
@RequiredArgsConstructor
public class UserFinderImpl implements UserFinder {

    private final UserRepository userRepository;

    @Override
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
