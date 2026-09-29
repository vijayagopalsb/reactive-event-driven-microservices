# Discovery Server

The `discovery-server` provides a Eureka registry for platform services. Eureka
clients register their service names and instance addresses with the server;
clients such as the API gateway can then resolve a logical service name when
routing requests.

This guide reflects the repository's current standalone Eureka configuration.

## Responsibilities

- Run the Eureka server on port `8761`.
- Accept registrations and registry lookups from Eureka clients.
- Provide the Eureka dashboard and Actuator health endpoint.

The server is not an application API gateway and does not route user requests
itself. In the current configuration, it does not register with another
Eureka server or fetch an upstream registry.

## Service discovery flow

```text
Auth Service --------\
                      +--> Eureka Discovery Server :8761
API Gateway ---------/             ^
                                    |
                           service lookup
                                    |
                             API Gateway
```

The gateway targets `lb://auth-service` for the configured auth route. Spring
Cloud resolves that logical service ID using Eureka and load-balances among
registered instances. The auth service also registers itself with Eureka.

## Configuration

Configuration is in `src/main/resources/application.yml`:

| Setting | Current value | Meaning |
|---|---|---|
| `server.port` | `8761` | HTTP port |
| `spring.application.name` | `discovery-server` | Application/service name |
| `eureka.client.register-with-eureka` | `false` | Server does not register itself |
| `eureka.client.fetch-registry` | `false` | Server does not fetch another registry |
| `eureka.server.renewal-percent-threshold` | `0.85` | Renewal threshold used by Eureka |

The gateway and auth-service configurations use
`http://discovery-server:8761/eureka/` as the registry URL in the Compose
network. Compose publishes port `8761` on the host.

## Health and local operation

Docker Compose checks the server at
`http://localhost:8761/actuator/health` and uses that health check to determine
when the discovery service is ready. The Eureka dashboard is available at
`http://localhost:8761/` when the local service is running.

Start the complete local stack from the repository root:

```shell
docker compose up --build
```

Build the discovery server and its Maven dependencies:

```shell
mvn -pl discovery-server -am verify
```

## Code map

```text
src/main/java/com/reactiveevent/platform/DiscoveryServerApplication.java
src/main/resources/application.yml
```

`DiscoveryServerApplication` enables the Eureka server using
`@EnableEurekaServer`. The module uses Spring Boot Actuator for health checks
and includes the platform logging starter.

## Current limitations and deployment considerations

- This repository configures one Eureka server instance for local development;
  it does not show a highly available peer cluster.
- The Compose health check establishes process-level readiness for the local
  setup; production readiness and registry health require deployment-specific
  monitoring.
- Registry access controls and transport security are not configured in the
  current application configuration. Restrict and secure the registry
  appropriately in a deployed environment.
- Service clients need network reachability to the registry and compatible
  Eureka client configuration.
