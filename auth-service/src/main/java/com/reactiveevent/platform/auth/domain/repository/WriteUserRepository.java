package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.user.User;
import reactor.core.publisher.Mono;

/**
 * WriteUserRepository — write operations on the users table.
 *
 * Separated from UserRepository (read) intentionally.
 * Read and write operations have different transaction characteristics:
 *   Reads  → can be run on read replicas, no transaction needed
 *   Writes → must go to the primary DB, run inside a transaction
 *
 * Having separate interfaces makes this distinction explicit.
 * A use case that only reads depends only on UserRepository.
 * A use case that writes depends on WriteUserRepository.
 * Neither knows about the other's existence.
 */
public interface WriteUserRepository {

    /**
     * Persist a new User to the users table.
     * Called by: CreateUserUseCaseImpl after building a new User aggregate.
     *
     * Returns the saved User — same object with same ID.
     * Why return it? The caller (use case) needs the confirmed saved state
     * to continue building the UserProvider and UserResponse.
     *
     * The implementation uses INSERT — this is for NEW users only.
     * Updating an existing user is a separate operation (not needed yet).
     */
    Mono<User> save(User user);
}
