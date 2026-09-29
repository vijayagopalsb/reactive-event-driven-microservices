# Common Domain

`common-domain` contains the shared business model for identity and
authorization. It is the domain layer of the common-library foundation and
does not depend on Spring, a database driver, or a messaging client.

## Model overview

| Concept | Responsibility |
|---|---|
| `User` | Identity: ID, email, and account status |
| `UserProvider` | Authentication method associated with a user: local credentials or an OAuth provider |
| `Role` | Named authorization group |
| `Permission` | Named fine-grained authorization capability |
| `AuthProvider` | Supported providers: `LOCAL`, `GOOGLE`, and `GITHUB` |
| `AuthToken` / `RefreshToken` | Token value types |
| `UserId`, `UserProviderId`, `RoleId`, `PermissionId` | Typed identity wrappers |
| `UserStatus` / `UserRole` | Account status and the currently modeled role names |

The model separates three concerns:

```text
User          -> who the person is
UserProvider  -> how the person authenticates
Role/Permission -> what the person is allowed to do
```

For example, a user's password hash is held by the `UserProvider` local
authentication record rather than by `User`. Role membership is modeled
separately through role assignments; a role is not an intrinsic field on the
user.

## Domain behavior and events

`User` and `UserProvider` expose factory methods for creating new instances and
rehydrating persisted state. `User` supports activate, deactivate, and block
operations; `UserProvider` enforces that passwords can only be changed for a
local provider.

The base types include entity identity/timestamps, aggregate roots, and a
domain-event collection. `UserCreatedEvent` and `UserLoggedInEvent` are
modeled as immutable events with an occurrence timestamp. The presence of
these event types does not imply that every state transition currently
registers or publishes a domain event; service behavior and the Kafka event
contracts are documented separately.

## Design boundaries

- Domain IDs wrap UUID values to make identity types explicit.
- OAuth providers are represented using the provider enum and external
  provider ID; password hashes are only relevant to local authentication.
- `UserRole` mirrors the seeded role names (`ADMIN`, `MANAGER`, `USER`); the
  database role records and assignments remain the persistence source of
  truth.
- The model uses Lombok for generated accessors and null checks.

## Code location

```text
src/main/java/com/reactiveevent/platform/common/domain/
├── auth/
├── base/
├── permission/
├── role/
└── user/
```

## Build

From the repository root:

```shell
mvn -pl common-libraries/common-domain -am verify
```
