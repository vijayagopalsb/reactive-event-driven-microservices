# Auth Service

The `auth-service` is the platform's identity and access management service.
It authenticates users, manages user accounts and role assignments, issues
JWTs, and publishes login events for asynchronous audit processing.

This guide describes the implementation currently in this repository. It is
not a claim that the service has every capability expected of a production
identity provider.

## Responsibilities and boundaries

| Responsibility | Current implementation |
|---|---|
| Local authentication | Email/password login with BCrypt password verification |
| Federated authentication | Google and GitHub OAuth2 authorization-code flows |
| Token issuance | RSA-signed JWT access and refresh tokens |
| User administration | Create/list/read users and assign roles |
| Authorization data | Roles and permissions loaded from MySQL and included in access tokens |
| Persistence | Reactive MySQL access through R2DBC |
| Service discovery | Registers with Eureka |
| Login events | Publishes login-success and invalid-credential events to Kafka |

The service is built with Spring WebFlux and Reactor. HTTP controllers translate
requests into shared commands and delegate to use-case implementations.
Application ports describe external capabilities; R2DBC, OAuth, JWT, and Kafka
adapters provide concrete integrations inside this service.

## Authentication flows

### Local email/password login

```text
Client
  -> POST /auth/login
  -> LoginUseCaseImpl
       -> find user and LOCAL provider
       -> check account status and BCrypt password
       -> load roles and permissions
       -> issue access and refresh JWTs
       -> publish UserLoggedIn event (best effort)
  <- LoginResult
```

Invalid credentials are returned using a generic authentication error rather
than identifying whether the email or password was incorrect. An invalid
credential attempt also triggers a login-failure event. The success-event
publisher is best effort: a Kafka publishing failure is logged and does not
turn valid credentials into a failed login.

The OAuth login implementation does not currently use these Kafka publishing
ports; the documented login events apply to the local login flow.

### Google/GitHub OAuth2 login

```text
Client -> GET /auth/oauth2/{google|github}/url
       <- authorization URL and short-lived HttpOnly state cookie

Provider -> GET /auth/oauth2/{provider}/callback?code=...&state=...
         -> validate state and browser cookie
         -> exchange authorization code and fetch provider profile
         -> find or provision user/provider records
         -> issue platform JWTs
```

OAuth state is short-lived and held in process memory. This implementation
therefore requires a single auth-service instance for consistent OAuth state
validation; horizontally scaling it requires a shared state store and
appropriate deployment configuration.

## HTTP API

Controller paths below are relative to the service. The service listens on
port `9090` by default.

| Method | Path | Purpose | Access |
|---|---|---|---|
| `POST` | `/auth/login` | Local email/password login | Public |
| `GET` | `/auth/oauth2/{provider}/url` | Create OAuth authorization URL and state cookie | Public |
| `GET` | `/auth/oauth2/{provider}/callback` | Complete OAuth login | Public callback |
| `POST` | `/users` | Create a local user | `ROLE_ADMIN` |
| `GET` | `/users` | List users and their roles | `ROLE_ADMIN` |
| `GET` | `/users/{id}` | Read a user | `ROLE_ADMIN` |
| `POST` | `/users/{id}/roles` | Assign a role | `ROLE_ADMIN` |
| `GET` | `/users/roles` | List assignable roles | `ROLE_ADMIN` |

The explicitly configured gateway route currently forwards
`/api/v1/auth/**` to this service, stripping the `/api/v1` prefix. It does not
define a corresponding `/users/**` route; the management endpoints are
available on the service itself unless gateway routing is extended.

The service exposes Actuator health, info, and metrics endpoints according to
its configuration. Health and info are permitted by the service security
configuration; other actuator paths require authentication.

## Security and tokens

- Local passwords are verified using BCrypt. Password hashes are stored on
  local `user_providers` records, not on the user identity record.
- OAuth callbacks require matching state query and cookie values; state is
  single-use, provider-bound, and expires after five minutes.
- JWTs are signed using RS256. The auth service needs the RSA private key to
  sign tokens; downstream verifiers need only the public key.
- Access tokens default to 15 minutes and contain subject, email, provider,
  roles, permissions, issuer, and issue/expiry times.
- Refresh tokens default to seven days and contain the user subject, issuer,
  and issue/expiry times. A refresh-token exchange endpoint is not currently
  exposed by this service.
- `ROLE_ADMIN` is enforced on user-management controller methods using
  reactive method security.

Key file paths are configured with `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`.
Never commit real private keys or provider client secrets. Repository
development defaults and Compose settings are not production secret
management.

## Persistence and event integration

The service uses the schema and seed scripts in [`mysql-init/`](../../mysql-init/):

- `users` holds identity and account status.
- `user_providers` stores local or OAuth authentication identities.
- `roles` and `permissions` define authorization data.
- `user_roles` and `role_permissions` represent the many-to-many assignments.

Kafka topics default to:

| Event | Topic | Current behavior |
|---|---|---|
| Successful local login | `auth.login-succeeded.v1` | Published with user ID as message key; audit consumer validates and logs it |
| Invalid local credentials | `auth.login-failed.v1` | Published without user identity; audit consumer validates and logs it |

The audit service currently logs consumed events; it does not persist an audit
record. The login event publishing path is not an outbox and does not guarantee
durable delivery if Kafka is unavailable.

## Configuration

The main configuration is in `src/main/resources/application.yml`. Important
environment overrides include:

| Variable | Purpose |
|---|---|
| `SPRING_R2DBC_URL` | Reactive MySQL connection URL |
| `SPRING_R2DBC_USERNAME`, `SPRING_R2DBC_PASSWORD` | Database credentials |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap address |
| `APP_KAFKA_TOPICS_USER_LOGGED_IN` | Successful-login topic |
| `APP_KAFKA_TOPICS_LOGIN_FAILED` | Invalid-credentials topic |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | RSA PEM key file paths |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | Google OAuth configuration |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | GitHub OAuth configuration |
| `OAUTH2_STATE_COOKIE_SECURE` | Whether the OAuth state cookie requires HTTPS |

The service also registers with Eureka using the configured
`EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`. Docker Compose supplies the
service-network values for local execution.

## Build and run

Build this module and its Maven dependencies from the repository root:

```shell
mvn -pl auth-service -am verify
```

Start the repository's full Compose environment, which provides MySQL, Kafka,
Redis, Eureka, and the other platform services:

```shell
docker compose up --build
```

The auth container expects the RSA key files mounted at the configured paths.
Google/GitHub login additionally requires valid provider credentials and
registered callback URLs. The seed SQL includes a development bootstrap admin
account; use it only in a disposable local environment and never as a
production credential.

## Code map

```text
src/main/java/com/reactiveevent/platform/auth/
├── api/             HTTP controllers
├── application/     Use-case implementations, ports, and application events
├── domain/repository/ Persistence-facing repository contracts
└── infrastructure/  R2DBC, OAuth, JWT/security, Kafka, and error adapters
```

Shared domain types, API records, and use-case interfaces are documented in
the [common-libraries guide](../../common-libraries/docs/README.md).

## Current limitations

- OAuth state is process-local and is not backed by the Redis container.
- Login-event delivery is not transactional with database changes and has no
  outbox/retry persistence.
- The audit consumer logs events but does not store them.
- Refresh tokens are issued, but there is no refresh endpoint or token
  revocation mechanism in this service.
- Explicit gateway routing currently does not include the user-management
  endpoints.
- Local Compose configuration is for development, not a production
  deployment.
