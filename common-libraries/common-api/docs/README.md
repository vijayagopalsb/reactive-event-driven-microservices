# Common API Contracts

`common-api` defines the data records used to carry inputs into application
operations and results back to callers. These are contracts, not HTTP
controllers: transport endpoints and status-code mapping live in the owning
service.

## Current contracts

| Area | Types | Purpose |
|---|---|---|
| Authentication | `LoginCommand`, `OAuthCallbackCommand` | Inputs for local login and OAuth authorization-code callbacks |
| Authentication | `LoginResult` | Access-token and refresh-token result |
| User management | `CreateUserCommand`, `AssignRoleCommand` | Inputs for administrative user and role operations |
| User management | `UserResponse`, `RoleResponse` | User and role data returned to callers |

Commands distinguish local credentials from OAuth callback data rather than
combining both flows into one request with optional fields. Response records
are separate from domain entities so a service can shape output without
returning its internal aggregate objects.

## Contract considerations

- These Java records are shared module types; they do not, by themselves,
  define a versioned public HTTP or Kafka schema.
- Some records intentionally refer to domain types such as `AuthProvider`,
  `UserRole`, `UserStatus`, `AuthToken`, and `RefreshToken`. This is a current
  compile-time coupling between API contracts and the domain module.
- Lombok `@NonNull` adds null checks, but it is not a complete request
  validation policy. HTTP validation and business rules must be enforced by
  the service/use-case path.
- Sensitive input such as a raw password is accepted by a command but must
  never be included in responses or logs.

## Code location

```text
src/main/java/com/reactiveevent/platform/common/api/
├── auth/
└── user/
```

## Build

From the repository root:

```shell
mvn -pl common-libraries/common-api -am verify
```
