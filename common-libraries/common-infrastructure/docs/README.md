# Common Infrastructure

`common-infrastructure` is intended to be the shared infrastructure-oriented
module in the common-library Maven aggregator. Its POM currently declares
dependencies for Spring Boot, WebFlux, R2DBC/MySQL, Kafka, reactive Redis, and
Logstash logging.

## Current implementation status

At this point, the module does **not** contain reusable database repositories,
Kafka adapters, Redis integrations, auto-configuration, or other concrete
infrastructure implementations. Its Java source currently consists only of
an IntelliJ-generated sample `Main` class. A dependency in the POM makes a
technology available at compile/runtime; it does not mean the module has
implemented an adapter for that technology.

Concrete infrastructure code for the auth capability currently lives inside
`auth-service`, including its R2DBC repositories, OAuth provider adapters,
JWT integration, and Kafka publishers. Redis is included in the local Compose
environment, but OAuth state is currently kept in auth-service process
memory, not in Redis.

## Declared dependency scope

The module currently depends on:

- `common-domain` and `common-application`
- Spring Boot core and WebFlux
- Spring Data R2DBC and the MySQL R2DBC driver
- Spring Kafka
- Reactive Spring Data Redis
- Logstash Logback encoder
- Lombok (provided scope)

This is a broad technology set for a module with no corresponding reusable
adapters yet. As the platform grows, dependencies should be retained here only
when they support concrete infrastructure functionality shared across
services. Service-specific adapters should remain in their owning service
unless there is a clear reuse boundary.

## Intended responsibility

When shared infrastructure code is introduced, this module should host
cross-service implementations of clearly defined ports or reusable
configuration. It should not become a catch-all for every service's
infrastructure. Keep business rules in the domain/application layers and
service-specific integration details close to the service that owns them.

Before adding an adapter here, document:

1. Which application/domain port it implements.
2. Which services consume it and why it is genuinely shared.
3. How configuration, lifecycle, errors, and tests are handled.

## Code location

```text
src/main/java/com/reactiveevent/platform/
```

## Build

From the repository root:

```shell
mvn -pl common-libraries/common-infrastructure -am verify
```
