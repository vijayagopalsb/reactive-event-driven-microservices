# API Gateway

The `api-gateway` is the platform's reactive HTTP edge. It validates JWTs
before protected requests enter the service network and routes authentication
requests to the auth service using service discovery.

This guide describes the current configuration, not a complete production
gateway policy.

## Responsibilities

- Accept external HTTP traffic on port `8082`.
- Validate bearer JWT signatures using the configured RSA public key.
- Validate the configured JWT issuer (`auth-service` by default).
- Route the explicitly configured auth API path to the registered
  `auth-service` through Eureka.
- Enable Spring Cloud Gateway's discovery-based route locator.
- Apply the configured global CORS policy.

The gateway is built on Spring Cloud Gateway and WebFlux. The runtime
configuration uses reactive JWT resource-server support.

## Request and routing flow

```text
Client
  -> API Gateway :8082
       -> validate bearer JWT when route requires authentication
       -> resolve destination through Eureka
       -> forward to Auth Service :9090
```

The explicit route is:

| Public path | Destination | Path transformation |
|---|---|---|
| `/api/v1/auth/**` | `lb://auth-service` | `StripPrefix=2` |

For example, `/api/v1/auth/login` is forwarded as `/auth/login`, and
`/api/v1/auth/oauth2/google/url` is forwarded as
`/auth/oauth2/google/url`.

The discovery locator is also enabled with lowercase service IDs. It can
expose discovery-based routes in addition to the explicit route. Any such
route is still subject to the gateway's security policy; only the explicitly
listed auth login, OAuth, health, and info paths are public.

## Security policy

| Path | Gateway policy |
|---|---|
| `/api/v1/auth/login` | Public |
| `/api/v1/auth/oauth2/**` | Public |
| `/actuator/health`, `/actuator/info` | Permitted by the security chain |
| Other paths | Require a valid bearer JWT |

The JWT decoder loads an RSA public key from the path set by `JWT_PUBLIC_KEY`
(default `/run/secrets/jwt_public_key`) and checks the issuer configured by
`JWT_ISSUER` (default `auth-service`). Token signing is performed by the auth
service; the gateway only verifies tokens and does not need the private key.

The gateway security chain disables CSRF and configures JWT resource-server
authentication. The application's current global CORS settings allow every
origin, method, and header. This is permissive for development and should be
restricted to known client origins and required methods/headers before
deployment.

## Configuration

The main settings are in `src/main/resources/application.yml`:

| Setting | Current value/purpose |
|---|---|
| `server.port` | `8082` |
| `spring.application.name` | `api-gateway` |
| `spring.cloud.gateway.routes` | Explicit auth-service route |
| `spring.cloud.gateway.discovery.locator.enabled` | Enables discovery-derived routes |
| `eureka.client.service-url.defaultZone` | Eureka registry URL |
| `jwt.issuer` / `JWT_ISSUER` | Expected token issuer |
| `jwt.public-key` / `JWT_PUBLIC_KEY` | RSA public-key PEM path |
| `spring.cloud.gateway.globalcors` | Current permissive CORS policy |

Docker Compose connects the gateway to `discovery-server` and mounts the JWT
public key at `/run/secrets/jwt_public_key`. The gateway does not require the
JWT private key.

## Running and building

Build the gateway and its Maven dependencies from the repository root:

```shell
mvn -pl api-gateway -am verify
```

Run the complete local platform with Docker Compose:

```shell
docker compose up --build
```

Once the gateway and discovery server are running, the explicit local login
route is:

```text
POST http://localhost:8082/api/v1/auth/login
```

The request contract and authentication behavior are described in the
[auth-service guide](../../auth-service/docs/README.md).

## Code map

```text
src/main/java/com/reactiveevent/platform/gateway/
├── ApiGatewayApplication.java
└── security/
    └── GatewaySecurityConfig.java
```

## Current limitations and operational notes

- The explicit application route covers `/api/v1/auth/**`; it does not define
  a dedicated `/api/v1/users/**` route for the auth service's admin APIs.
- Discovery-based routing is enabled, so review the externally reachable
  service routes rather than assuming only the explicit route exists.
- The global CORS policy is wildcard and should be tightened for a deployed
  frontend.
- The gateway's public-key decoder requires the key file to exist at startup.
- Health and info paths are permitted by the security rules; actuator endpoint
  exposure is configured separately.
- Local Compose configuration and security defaults are not a production
  deployment profile.
