-- =============================================================================
-- AUTH SERVICE SEED DATA
-- =============================================================================
-- This file bootstraps the minimum data needed to run the system.
--
-- IMPORTANT: UUIDs are hardcoded here intentionally.
--   - Application code uses random UUIDs (Java: UUID.randomUUID())
--   - Seed data uses fixed UUIDs so FK references between INSERTs work
--   - These IDs are stable across environments (dev, staging, prod)
--
-- Bootstrap admin credentials:
--   Email   : admin@example.com
--   Password: Admin@1234
--   Hash    : bcrypt, cost factor 10
-- =============================================================================


-- =============================================================================
-- STEP 1: Roles
-- Insert roles first — users and permissions will reference these IDs.
-- =============================================================================

INSERT INTO roles (id, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN'),
    ('00000000-0000-0000-0000-000000000002', 'MANAGER'),
    ('00000000-0000-0000-0000-000000000003', 'USER');

-- Why these three?
--   ADMIN   : full system access — manages users, roles, permissions
--   MANAGER : elevated access — can view all users, assign USER role
--   USER    : default role — basic read access to their own data


-- =============================================================================
-- STEP 2: Permissions
-- Fine-grained actions that roles can grant.
-- Naming convention: RESOURCE_ACTION (uppercase, underscore-separated)
-- =============================================================================

INSERT INTO permissions (id, name) VALUES
    ('00000000-0000-0000-0001-000000000001', 'USER_READ'),
    ('00000000-0000-0000-0001-000000000002', 'USER_WRITE'),
    ('00000000-0000-0000-0001-000000000003', 'USER_DELETE'),
    ('00000000-0000-0000-0001-000000000004', 'ROLE_ASSIGN'),
    ('00000000-0000-0000-0001-000000000005', 'PERMISSION_ASSIGN');

-- USER_READ        : can view user list and user details
-- USER_WRITE       : can create and update users
-- USER_DELETE      : can deactivate or block users
-- ROLE_ASSIGN      : can assign/remove roles from users
-- PERMISSION_ASSIGN: can manage permission assignments (future use)


-- =============================================================================
-- STEP 3: Role → Permission mappings
-- Wire each role to the permissions it grants.
-- =============================================================================

-- ADMIN role gets ALL permissions
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0001-000000000001'), -- ADMIN → USER_READ
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0001-000000000002'), -- ADMIN → USER_WRITE
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0001-000000000003'), -- ADMIN → USER_DELETE
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0001-000000000004'), -- ADMIN → ROLE_ASSIGN
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0001-000000000005'); -- ADMIN → PERMISSION_ASSIGN

-- MANAGER role gets read + write (no delete, no role/permission management)
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0001-000000000001'), -- MANAGER → USER_READ
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0001-000000000002'); -- MANAGER → USER_WRITE

-- USER role gets read only
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0001-000000000001'); -- USER → USER_READ


-- =============================================================================
-- STEP 4: Bootstrap admin user
-- One user. LOCAL provider. ADMIN role.
-- This is the only user that is seeded — all others are created via API.
-- =============================================================================

-- 4a. Identity row
INSERT INTO users (id, email, status, created_at) VALUES
    ('00000000-0000-0000-0002-000000000001', 'admin@example.com', 'ACTIVE', NOW());

-- 4b. Authentication method: LOCAL with bcrypt password
--     Password : Admin@1234
--     Algorithm: BCrypt, cost=10
INSERT INTO user_providers (id, user_id, provider, external_id, password_hash, created_at) VALUES
    (
        '00000000-0000-0000-0003-000000000001',          -- provider row id
        '00000000-0000-0000-0002-000000000001',          -- → admin user
        'LOCAL',                                          -- authentication method
        NULL,                                             -- no external OAuth2 id
        '$2a$10$IkTHedCUAFxV1Vg1g9PLV.x58my.8CyK8xY0Zn9hz/Wklmjwwq.he', -- bcrypt hash
        NOW()
    );

-- 4c. Assign ADMIN role to the admin user
INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0000-000000000001'); -- admin → ADMIN
