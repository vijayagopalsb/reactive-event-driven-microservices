# Common Application Contracts

`common-application` publishes interfaces for the platform's application
operations. These contracts let service implementations expose use cases
without making callers depend on the implementation classes or their
infrastructure.

## Current use cases

| Interface | Operation | Reactive result |
|---|---|---|
| `LoginUseCase` | Authenticate with local email and password | `Mono<LoginResult>` |
| `OAuthLoginUseCase` | Complete Google or GitHub OAuth login | `Mono<LoginResult>` |
| `CreateUserUseCase` | Create a local user account | `Mono<UserResponse>` |
| `AssignRoleUseCase` | Assign an existing role to a user | `Mono<Void>` |

The interfaces use `common-api` for inputs and outputs and Project Reactor
types for asynchronous completion. They describe application operations; they
do not contain the service's persistence queries, controller mappings, or
Spring bean configuration.

## Implementation boundary

The concrete implementations currently live in `auth-service` under its
application layer. That service composes domain repositories and application
ports with infrastructure adapters such as password verification, JWT
generation, OAuth profile fetching, and Kafka publishing.

The split is intended to keep use-case callers insulated from those concrete
mechanisms. An interface is only a contract, however: the interface's Javadoc
can describe expected behavior, but actual behavior is determined by the
service implementation.

## Code location

```text
src/main/java/com/reactiveevent/platform/common/application/
├── auth/
└── user/
```

## Build

From the repository root:

```shell
mvn -pl common-libraries/common-application -am verify
```
