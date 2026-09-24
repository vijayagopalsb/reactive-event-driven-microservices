# JWT Authentication Explained with the Feynman Technique

## 1. The office-building analogy

Imagine the application as an office building:

- **User**: a visitor
- **Auth service**: the security desk
- **JWT access token**: a temporary visitor badge
- **Refresh token**: a special badge used to request a new visitor badge
- **Private key**: a secret stamp kept only by the security desk
- **Public key**: a stamp sample used by other services to verify badges

## 2. What happens during login?

The client sends:

```http
POST /auth/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "Admin@1234",
  "provider": "LOCAL"
}
```

The application flow is:

```text
Controller
   ↓
LoginUseCase
   ↓
Find user by email
   ↓
Check user status
   ↓
Load LOCAL provider and password hash
   ↓
Compare password with BCrypt
   ↓
Load roles and permissions
   ↓
Generate access token and refresh token
   ↓
Return both tokens to client
```

In this project, the main classes are:

```text
AuthController.java
LoginUseCaseImpl.java
PasswordVerifierImpl.java
TokenGeneratorImpl.java
```

The password is not placed inside the JWT. It is checked once during login. If it matches the stored BCrypt hash, the server issues tokens.

## 3. What is an access token?

An access token is proof that the user has already authenticated.

Conceptually, its payload may look like:

```json
{
  "sub": "user-id",
  "email": "admin@example.com",
  "provider": "LOCAL",
  "roles": ["ROLE_ADMIN"],
  "permissions": ["USER_READ", "USER_WRITE"],
  "iss": "auth-service",
  "iat": 1727000000,
  "exp": 1727000900
}
```

This project configures access tokens to expire after:

```yaml
access-token-expiry: 900
```

That is 900 seconds, or 15 minutes.

The client sends the token on protected requests:

```http
GET /api/v1/auth/users
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

`Bearer` means that the caller is presenting the token it carries.

## 4. Is a JWT encrypted?

Usually, no. A JWT is normally **signed**, not encrypted.

It has three parts:

```text
header.payload.signature
```

The header and payload can be decoded by anyone, so passwords and other secrets must never be placed inside them.

The signature protects integrity:

> Was this token created by the auth service, and was it changed afterward?

## 5. How RSA signing works

This project uses RSA:

```text
Private key → creates the signature
Public key  → verifies the signature
```

During token generation, the auth service uses `jwt_private_key.pem`. Only the auth service should have this key.

During token validation, the gateway and other services use `jwt_public_key.pem`. The public key can be distributed because it cannot create valid tokens.

```text
Auth service:
    User data + private key
              ↓
        Signed JWT

Gateway:
    JWT + public key
              ↓
       Valid or invalid?
```

## 6. What does the gateway do?

The normal request path is:

```text
Client → API Gateway → Auth service or another microservice
```

For a protected request, the gateway checks:

1. A bearer token exists.
2. The JWT structure is valid.
3. The RSA signature is valid.
4. The token is not expired.
5. The issuer is `auth-service`.
6. Required claims are present.

If validation fails:

```http
401 Unauthorized
```

The gateway does not need the private key. It only needs the public key.

Downstream services should also validate tokens independently as defense in depth.

## 7. Roles and permissions

A **role** is a group:

```text
ROLE_ADMIN
ROLE_MANAGER
ROLE_USER
```

A **permission** is a specific action:

```text
USER_READ
USER_WRITE
USER_DELETE
ROLE_ASSIGN
```

For example:

```java
@PreAuthorize("hasRole('ADMIN')")
```

checks for `ROLE_ADMIN`.

```java
@PreAuthorize("hasAuthority('USER_READ')")
```

checks for `USER_READ`.

Authentication answers:

> Who are you?

Authorization answers:

> What are you allowed to do?

## 8. Why do we need a refresh token?

An access token should be short-lived. If somebody steals a 15-minute access token, its useful lifetime is limited.

Forcing the user to log in every 15 minutes would be inconvenient, so the refresh token is used to obtain a new access token.

The intended flow is:

```text
1. User logs in.
2. Server returns an access token and refresh token.
3. Client uses the access token for API calls.
4. Access token expires after 15 minutes.
5. Client sends the refresh token to /auth/refresh.
6. Server validates the refresh token.
7. Server issues a new access token.
```

This project currently generates refresh tokens, but the refresh feature is incomplete because it does not yet provide:

- A refresh endpoint
- Refresh-token rotation
- Refresh-token revocation
- Replay detection
- Logout invalidation

## 9. Access token versus refresh token

| Feature | Access token | Refresh token |
|---|---|---|
| Used for | Normal API requests | Getting a new access token |
| Lifetime | Short, about 15 minutes | Longer, about 7 days |
| Contains | Identity, roles, permissions | Usually user ID and token metadata |
| Sent to | Gateway and APIs | Auth service only |
| Exposure risk | High | Very high |
| Rotation | Usually not required per request | Should be rotated |

A refresh token should not be sent to every microservice.

## 10. The login flow in this project

### Step 1: Controller receives the request

`AuthController` receives the HTTP request and forwards it to the use case. The controller should stay thin.

### Step 2: Provider is checked

Local password login is handled by the local login use case. Google and GitHub use the OAuth flow.

### Step 3: The user is found

The application searches for the user by email. If no user is found, it returns the generic message `Invalid credentials` rather than revealing whether the email exists.

### Step 4: Account status is checked

```text
ACTIVE     → continue
INACTIVE   → reject
BLOCKED    → reject
```

### Step 5: The password hash is loaded

The password hash is stored in:

```text
user_providers.password_hash
```

It is not stored directly on the `users` table.

### Step 6: The password is verified

```text
BCrypt.matches(rawPassword, storedHash)
```

The raw password is never stored.

### Step 7: Authorization data is loaded

```text
User → Roles → Permissions
```

For example:

```text
admin@example.com
    ↓
ADMIN
    ↓
USER_READ, USER_WRITE, USER_DELETE, ROLE_ASSIGN
```

### Step 8: Tokens are generated

`TokenGeneratorImpl` creates the access token and refresh token.

### Step 9: Tokens are returned

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

## 11. Why does the server not query the database on every request?

Without JWT:

```text
Every request → database lookup
```

With JWT:

```text
Login → database lookup
Every request → signature verification
```

This reduces database traffic and allows distributed services to verify tokens independently.

The tradeoff is that if a role is removed after a token is issued, the old token may still contain that role until it expires. Short-lived access tokens reduce this risk.

## 12. OAuth login is different

The OAuth flow is:

```text
1. Client requests an authorization URL.
2. Auth service creates random state.
3. Browser goes to Google or GitHub.
4. Provider authenticates the user.
5. Provider redirects back with code and state.
6. Auth service validates the state.
7. Auth service exchanges the code for a provider access token.
8. Auth service fetches the provider profile.
9. Auth service finds or creates a local user.
10. Auth service creates the application's JWT.
```

The provider token is not normally the application's token. The provider proves that the person authenticated with Google or GitHub, and the auth service then creates its own JWT containing application roles and permissions.

## 13. The complete system

```text
                    Login
                      │
                      ▼
                 Auth Service
          checks email and password
                      │
          signs tokens with private key
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
    Access token            Refresh token
       15 minutes               7 days
          │                       │
          │                 Auth service only
          ▼
       Gateway
   verifies with public key
          │
          ▼
     Microservice
  checks roles/permissions
          │
          ▼
       Response
```

## 14. Important rules

1. Never put passwords in JWTs.
2. Never give the private key to the gateway or other services.
3. The private key signs; the public key verifies.
4. Access tokens should be short-lived.
5. Refresh tokens need rotation and revocation.
6. Authentication and authorization are different.
7. A JWT is signed, not automatically encrypted.
8. The gateway should validate tokens, and downstream services should validate them too.
9. Roles describe groups; permissions describe actions.
10. JWT claims are not automatically current database truth until the token expires.

## Feynman self-test

Try to answer these without looking back:

1. Why can the gateway verify a JWT without having the private key?
2. Why should the access token expire faster than the refresh token?
3. What is the difference between `ROLE_ADMIN` and `USER_READ`?
4. What happens if someone changes the JWT payload manually?
5. Why is issuing a refresh token without a refresh endpoint incomplete?

### Answers

1. The public key verifies signatures but cannot create them.
2. A stolen access token should have a short useful lifetime.
3. `ROLE_ADMIN` is a group; `USER_READ` is an action permission.
4. Signature verification fails.
5. The client cannot safely obtain a new access token after expiration.
