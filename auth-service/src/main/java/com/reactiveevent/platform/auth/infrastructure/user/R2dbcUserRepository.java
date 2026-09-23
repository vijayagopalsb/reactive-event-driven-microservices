package com.reactiveevent.platform.auth.infrastructure.user;

import com.reactiveevent.platform.auth.domain.repository.UserRepository;
import com.reactiveevent.platform.common.domain.user.User;
import com.reactiveevent.platform.common.domain.user.UserId;
import com.reactiveevent.platform.common.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2dbcUserRepository — reads from the users table using reactive R2DBC.
 *
 * This is the infrastructure implementation of the UserRepository domain interface.
 * It is the ONLY place in the application that writes SQL for the users table reads.
 *
 * Row mapping explained:
 *   The users table now only has: id, email, status, created_at
 *   No password_hash (moved to user_providers).
 *   No role (moved to user_roles → roles RBAC tables).
 *   The mapping is therefore simple and clean.
 *
 * Error handling:
 *   We let Mono.empty() / Flux.empty() propagate naturally when no rows are found.
 *   The USE CASE decides what empty means (e.g. "invalid credentials", "not found").
 *   The repository just returns what the DB gives us — no business decisions here.
 */
@Repository
@RequiredArgsConstructor
public class R2dbcUserRepository implements UserRepository {

    private final DatabaseClient client;

    // -------------------------------------------------------------------------
    // findByEmail — used during LOGIN
    // The LoginUseCaseImpl calls this first to locate the user by email,
    // then loads their UserProvider separately to get the password hash.
    // -------------------------------------------------------------------------
    @Override
    public Mono<User> findByEmail(String email) {
        return client.sql("""
                SELECT id, email, status
                FROM users
                WHERE email = :email
                """)
                .bind("email", email)
                .map(row -> {
                    UUID id       = row.get("id", String.class) != null
                                    ? UUID.fromString(row.get("id", String.class))
                                    : null;
                    String emailVal = row.get("email", String.class);
                    String status   = row.get("status", String.class);

                    if (id == null || emailVal == null || status == null) {
                        throw new IllegalStateException("Corrupt users row: missing required fields");
                    }

                    return User.rehydrate(
                            UserId.of(id),
                            emailVal,
                            UserStatus.valueOf(status)
                    );
                })
                .one();
        // .one() → returns Mono.empty() if no row found, error if multiple rows found
        // We trust the UNIQUE constraint on email prevents multiple rows
    }

    // -------------------------------------------------------------------------
    // findById — used when assigning roles (verify user exists)
    // -------------------------------------------------------------------------
    @Override
    public Mono<User> findById(UserId userId) {
        return client.sql("""
                SELECT id, email, status
                FROM users
                WHERE id = :id
                """)
                .bind("id", userId.getValue().toString())
                .map(row -> {
                    UUID id       = UUID.fromString(row.get("id", String.class));
                    String email  = row.get("email", String.class);
                    String status = row.get("status", String.class);

                    if (email == null || status == null) {
                        throw new IllegalStateException("Corrupt users row: missing required fields");
                    }

                    return User.rehydrate(
                            UserId.of(id),
                            email,
                            UserStatus.valueOf(status)
                    );
                })
                .one();
    }

    // -------------------------------------------------------------------------
    // findAll — used by the admin user list endpoint (GET /users)
    // -------------------------------------------------------------------------
    @Override
    public Flux<User> findAll() {
        return client.sql("""
                SELECT id, email, status
                FROM users
                ORDER BY created_at DESC
                """)
                .map(row -> {
                    UUID id       = UUID.fromString(row.get("id", String.class));
                    String email  = row.get("email", String.class);
                    String status = row.get("status", String.class);

                    if (email == null || status == null) {
                        throw new IllegalStateException("Corrupt users row: missing required fields");
                    }

                    return User.rehydrate(
                            UserId.of(id),
                            email,
                            UserStatus.valueOf(status)
                    );
                })
                .all();
        // .all() → returns all rows as a Flux stream
    }
}
