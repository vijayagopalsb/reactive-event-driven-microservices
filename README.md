# Reactive Event-Driven Microservices

A backend platform for demonstrating how reactive programming, domain-driven
design, API security, service discovery, and event-driven integration fit
together in a multi-module Java microservices system.

> **Project status:** This repository contains a working foundation and an
> evolving set of services. This README describes the implementation that is
> present today; it does not imply that every production or enterprise
> capability is complete. There is currently no frontend application in this
> repository.

## What the platform does

The current business capability is identity and access management. The auth
service supports local email/password authentication and Google/GitHub OAuth2
login, issues JWT access and refresh tokens, and provides user and role
management. A gateway validates JWTs and routes requests. Successful logins
local successful logins and invalid-credential attempts are sent to Kafka for
an audit service to consume. A local login still succeeds if publishing its
success audit event fails.

The synchronous API flow and asynchronous event flow are intentionally
separate:

```text
                         +--------------------+
Client ---> API Gateway | JWT validation and |
                         | service routing    |
                         +---------+----------+
                                   |
                                   v
                         +--------------------+       +-------+
                         |    Auth Service    |------>| MySQL |
                         +--+--------------+--+       +-------+
                            |              ^
                  login events             | service lookup
                            v              |
                         +------+     +----+----------------+
                         | Kafka|     | Eureka Discovery    |
                         +--+---+     +---------------------+
                            |
                            v
                       +------------+
                       | Audit      |
                       | Service    |
                       +------------+

Service logs ---> Logstash ---> Elasticsearch ---> Kibana
```

## Current capabilities

| Capability | Current implementation |
|---|---|
| Authentication | Local password login and Google/GitHub OAuth2 login in `auth-service` |
| Authorization | JWT validation, roles, permissions, and user-role management |
| API entry point | Spring Cloud Gateway with JWT checks and auth routing |
| Service discovery | Eureka server and registered service clients |
| Event messaging | Kafka login-success and login-failure events |
| Audit consumer | Validates event type/schema and logs events; it does not yet persist an audit record |
| Data store | MySQL accessed reactively through R2DBC by the auth service |
| OAuth state | Short-lived state currently stored in the auth-service process; Redis is present in Compose but is not wired as this state store |
| Logging pipeline | Logstash, Elasticsearch, and Kibana services are defined for local development |
| Client application | Not included in the repository at this stage |

The Compose stack is a development environment, not a production deployment
blueprint. Production readiness would require environment-specific hardening,
secret management, deployment topology, resilience policies, and operational
controls appropriate to the target environment.

## Repository map

| Project area | Purpose |
|---|---|
| `common-libraries/` | Shared domain model, API contracts, application ports, and infrastructure module |
| `auth-service/` | Authentication, user management, security, persistence and Kafka publishing |
| `api-gateway/` | External gateway and JWT validation |
| `discovery-server/` | Eureka service registry |
| `audit-service/` | Kafka audit-event consumer |
| `platform-logging-starter/` | Reusable logging auto-configuration |
| `docker-compose.yml` | Local infrastructure and service orchestration |
| `mysql-init/` | Auth database schema and seed data |
| `logstash/` | Logstash pipeline configuration |

## Documentation

Documentation is organized around the repository projects and is being built
incrementally. This page stays the project landing page; detailed design and
implementation notes live beside the projects they describe.

### Foundation: shared libraries

The shared Maven project contains four modules. Start with its overview, then
follow the module-specific guides:

- [Common libraries overview](common-libraries/docs/README.md)
- [Domain model](common-libraries/common-domain/docs/README.md)
- [API contracts](common-libraries/common-api/docs/README.md)
- [Application contracts](common-libraries/common-application/docs/README.md)
- [Common infrastructure](common-libraries/common-infrastructure/docs/README.md)

The common-infrastructure guide distinguishes the module's declared framework
dependencies from the concrete adapters currently implemented in the services.

### Services

- [Auth service](auth-service/docs/README.md) — authentication and OAuth flows,
  user management, JWTs, persistence, and login-event integration.

Documentation for the remaining services will be added in subsequent
increments.

## Technology foundation

- Java 21 and Maven multi-module build
- Spring Boot / Spring WebFlux and Project Reactor
- Spring Security and JWT
- Spring Cloud Gateway and Netflix Eureka
- Apache Kafka
- MySQL with R2DBC
- Docker Compose
- Logstash, Elasticsearch, and Kibana

## Build and run locally

Prerequisites: JDK 21, Maven, and Docker with the Compose plugin.

Build the Maven reactor from the repository root:

```shell
mvn clean verify
```

Start the local Compose environment:

```shell
docker compose up --build
```

OAuth login also requires valid Google and/or GitHub OAuth client credentials
and matching callback URLs. Local Compose defaults are for development and
must not be treated as production credentials or configuration.

## Architectural direction

The codebase uses a layered separation across shared modules: domain concepts
are modeled separately from API data contracts, application use-case
interfaces, and infrastructure concerns. Service implementations provide the
concrete use cases and adapters. This separation is a foundation for the
showcase; module boundaries and production concerns will be documented and
refined as the platform evolves.
