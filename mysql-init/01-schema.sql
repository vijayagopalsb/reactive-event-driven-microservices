-- =============================================================================
-- AUTH SERVICE SCHEMA
-- =============================================================================
-- Design principle: each table has ONE responsibility.
--
-- users          → WHO you are        (identity)
-- user_providers → HOW you log in     (authentication method)
-- roles          → WHAT group you're in
-- permissions    → WHAT actions you can do
-- user_roles     → which user has which role     (many-to-many)
-- role_permissions→ which role has which actions (many-to-many)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- TABLE: users
-- Purpose: Pure identity. Who is this person? Nothing else.
-- Note:    No password here. No role here. No provider here.
--          Those belong to other tables.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id         CHAR(36)     NOT NULL,
    email      VARCHAR(255) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | BLOCKED
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users       PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);


-- -----------------------------------------------------------------------------
-- TABLE: user_providers
-- Purpose: HOW a user authenticates. One row per login method per user.
--
-- Examples:
--   LOCAL user  → provider='LOCAL',  external_id=NULL,  password_hash='$2a$...'
--   Google user → provider='GOOGLE', external_id='1098765432', password_hash=NULL
--   GitHub user → provider='GITHUB', external_id='12345678',   password_hash=NULL
--
-- A user can have multiple rows here (e.g. LOCAL + GOOGLE = account linking).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_providers (
    id            CHAR(36)     NOT NULL,
    user_id       CHAR(36)     NOT NULL,
    provider      VARCHAR(50)  NOT NULL,               -- LOCAL | GOOGLE | GITHUB
    external_id   VARCHAR(255) NULL,                   -- OAuth2 provider's user ID
    password_hash VARCHAR(255) NULL,                   -- only for LOCAL provider
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_providers              PRIMARY KEY (id),
    CONSTRAINT uq_user_providers_user_prov    UNIQUE (user_id, provider),   -- one provider type per user
    CONSTRAINT uq_user_providers_prov_ext     UNIQUE (provider, external_id), -- one account per provider ID
    CONSTRAINT fk_user_providers_user_id      FOREIGN KEY (user_id) REFERENCES users (id)
);


-- -----------------------------------------------------------------------------
-- TABLE: roles
-- Purpose: Named permission groups. e.g. ADMIN, MANAGER, USER
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    id   CHAR(36)     NOT NULL,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);


-- -----------------------------------------------------------------------------
-- TABLE: permissions
-- Purpose: Individual fine-grained actions. e.g. USER_READ, USER_DELETE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS permissions (
    id   CHAR(36)     NOT NULL,
    name VARCHAR(100) NOT NULL,

    CONSTRAINT pk_permissions      PRIMARY KEY (id),
    CONSTRAINT uq_permissions_name UNIQUE (name)
);


-- -----------------------------------------------------------------------------
-- TABLE: user_roles
-- Purpose: Which user has which role. Many-to-many join table.
--
-- Read as: "user X has role Y"
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,

    CONSTRAINT pk_user_roles         PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES roles (id)
);


-- -----------------------------------------------------------------------------
-- TABLE: role_permissions
-- Purpose: Which role grants which permissions. Many-to-many join table.
--
-- Read as: "role X grants permission Y"
-- The full chain: user → user_roles → roles → role_permissions → permissions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       CHAR(36) NOT NULL,
    permission_id CHAR(36) NOT NULL,

    CONSTRAINT pk_role_permissions              PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role_id      FOREIGN KEY (role_id)       REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_perm_id      FOREIGN KEY (permission_id) REFERENCES permissions (id)
);
