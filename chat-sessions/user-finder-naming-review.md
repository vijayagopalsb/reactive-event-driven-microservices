# `UserFinder` and Repository Naming Review

## Short answer

`UserFinder` is **not a repository** in the current design. It is an **application port**.

`UserProviderRepository` is a **domain repository interface**.

## Current dependency roles

```text
LoginUseCaseImpl
    │
    ├── UserFinder                  application port
    ├── UserProviderRepository      domain repository
    ├── PasswordVerifier             application port
    ├── TokenGenerator               application port
    ├── RoleRepository               domain repository
    └── PermissionRepository         domain repository
```

## `UserFinder`

```java
public interface UserFinder {
    Mono<User> findByEmail(String email);
}
```

Location:

```text
auth-service/.../application/ports/UserFinder.java
```

It represents the exact capability required by the login use case:

> Find a user by email.

Its adapter is:

```java
@Component
public class UserFinderImpl implements UserFinder {

    private final UserRepository userRepository;

    @Override
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
```

The flow is:

```text
LoginUseCaseImpl
    ↓
UserFinder
    ↓
UserFinderImpl
    ↓
UserRepository
    ↓
R2dbcUserRepository
    ↓
MySQL
```

Therefore, `UserFinder` is a **use-case-specific query port**, not a repository.

## `UserProviderRepository`

```java
public interface UserProviderRepository {
    Mono<UserProvider> findByEmailAndProvider(
            String email,
            AuthProvider provider
    );

    Mono<UserProvider> findByProviderAndExternalId(
            AuthProvider provider,
            String externalId
    );

    Mono<UserProvider> findByUserIdAndProvider(
            UserId userId,
            AuthProvider provider
    );

    Mono<UserProvider> save(UserProvider userProvider);
}
```

Location:

```text
auth-service/.../domain/repository/UserProviderRepository.java
```

This represents persistence operations for the `UserProvider` domain concept:

```text
Read UserProvider
Save UserProvider
```

Its infrastructure adapter is:

```text
R2dbcUserProviderRepository
```

Therefore, this is a repository abstraction.

## Is the naming inconsistent?

Slightly, but not incorrectly.

The project mixes two naming patterns because the interfaces represent different architectural concepts:

| Interface | Architectural role | Current name |
|---|---|---|
| Finds a user for login | Application query port | `UserFinder` |
| Loads and saves user providers | Domain repository | `UserProviderRepository` |
| Loads roles | Domain repository | `RoleRepository` |
| Loads permissions | Domain repository | `PermissionRepository` |
| Verifies passwords | Application port | `PasswordVerifier` |
| Generates JWTs | Application port | `TokenGenerator` |

## Should `UserFinder` exist?

There are two valid designs.

### Option 1: Keep `UserFinder`

```java
private final UserFinder userFinder;
```

This is a use-case-focused hexagonal design.

Advantages:

- `LoginUseCaseImpl` depends only on what login needs.
- It is easy to mock in unit tests.
- The use case does not depend on the complete `UserRepository` API.
- Login-specific lookup logic can be added later without expanding the use case dependency.

For example, the port could later become:

```java
Mono<User> findActiveUserByEmail(String email);
```

or:

```java
Mono<LoginIdentity> findLoginIdentity(String email);
```

### Option 2: Use `UserRepository` directly

```java
private final UserRepository userRepository;
```

Then:

```java
return userRepository.findByEmail(command.email());
```

This is also valid because `UserRepository` is already an abstraction and does not expose SQL or R2DBC.

Advantages:

- Fewer interfaces and adapters.
- Less boilerplate.
- More direct use of the DDD repository.
- Easier to understand in a small application.

## Recommendation for this project

Because the project is intentionally demonstrating DDD and hexagonal architecture, keeping the application port is reasonable.

However, `UserFinder` could be renamed to make its purpose clearer:

```java
UserQuery
```

or:

```java
UserLookup
```

Example:

```java
public interface UserQuery {
    Mono<User> findByEmail(String email);
}
```

Implementation:

```java
@Component
@RequiredArgsConstructor
public class UserQueryAdapter implements UserQuery {

    private final UserRepository userRepository;

    @Override
    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
```

Then the use case reads clearly:

```java
private final UserQuery userQuery;
```

and:

```java
return userQuery.findByEmail(command.email());
```

Suggested naming:

```text
UserQuery                       application port
UserQueryAdapter                infrastructure adapter
UserRepository                  domain repository
R2dbcUserRepository             infrastructure repository adapter
UserProviderRepository          domain repository
R2dbcUserProviderRepository     infrastructure repository adapter
```

## Repository versus port

A repository is usually associated with a domain aggregate:

```text
UserRepository
UserProviderRepository
RoleRepository
```

A port is defined by an application use case:

```text
UserQuery
PasswordVerifier
TokenGenerator
OAuthTokenExchanger
OAuthProfileFetcher
```

The distinction is about who owns the abstraction:

```text
Domain repository:
    The domain owns the persistence abstraction.

Application port:
    The application/use case owns the required capability.
```

## Final verdict

- `UserFinder` is **not a repository** in the current code.
- `UserProviderRepository` **is a repository**.
- The naming is acceptable, but `UserFinder` could be renamed to `UserQuery` or `UserLookup`.
- `UserFinderImpl` is correctly an adapter, although it is currently only a thin bridge.
- Do not rename it to `UserRepository` unless its architectural responsibility also changes.
