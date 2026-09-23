package com.reactiveevent.platform.auth.infrastructure.user;

import com.reactiveevent.platform.auth.domain.repository.WriteUserRepository;
import com.reactiveevent.platform.common.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * R2dbcWriteUserRepository — inserts new rows into the users table.
 *
 * This handles only WRITE operations (INSERT).
 * Read operations are in R2dbcUserRepository.
 *
 * Why manual INSERT instead of Spring Data's save()?
 *   Spring Data's ReactiveCrudRepository.save() does an "upsert" —
 *   it checks if the entity exists and does INSERT or UPDATE accordingly.
 *   We want explicit control: a new user is always an INSERT.
 *   Using manual SQL makes the intent unambiguous.
 *
 * Transaction note:
 *   CreateUserUseCaseImpl will call save(user) + save(userProvider) + saveUserRole()
 *   in sequence. In Phase 4.3, we'll wrap these in a reactive transaction so that
 *   if any step fails, all are rolled back. A partial user (user row without
 *   a provider row) would be inconsistent data.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcWriteUserRepository implements WriteUserRepository {

    private final DatabaseClient client;

    @Override
    public Mono<User> save(User user) {
        return client.sql("""
                INSERT INTO users (id, email, status, created_at)
                VALUES (:id, :email, :status, NOW())
                """)
                .bind("id",     user.getId().getValue().toString())
                .bind("email",  user.getEmail())
                .bind("status", user.getStatus().name())
                .fetch()
                // .rowsUpdated() tells us how many rows were affected.
                // We expect exactly 1. If 0, something went wrong.
                .rowsUpdated()
                .flatMap(rows -> {
                    if (rows == 0) {
                        return Mono.error(new IllegalStateException(
                            "INSERT into users failed — 0 rows affected for email: "
                            + user.getEmail()
                        ));
                    }
                    // Return the same user object — it hasn't changed,
                    // but the caller needs it to continue building the response.
                    return Mono.just(user);
                });
    }
}
