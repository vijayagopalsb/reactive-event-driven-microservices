package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.role.Role;
import com.reactiveevent.platform.common.domain.user.UserId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RoleRepository — read operations on the roles and user_roles tables.
 *
 * RBAC chain: user → user_roles → roles → role_permissions → permissions
 * This repository handles the user → roles part of that chain.
 */
public interface RoleRepository {

    /**
     * Load all roles assigned to a given user.
     * Used by: TokenGeneratorImpl to embed roles in the JWT claims.
     * Used by: CustomReactiveUserDetailsService to build Spring Security authorities.
     */
    Flux<Role> findRolesByUserId(UserId userId);

    /**
     * Find a role by its name.
     * Used by: CreateUserUseCaseImpl to resolve "ADMIN" → Role{id=..., name="ADMIN"}
     * so it can create the user_roles row with the correct roleId.
     */
    Mono<Role> findByName(String name);

    /**
     * Find a role by its UUID string.
     * Used by: AssignRoleUseCaseImpl to validate the roleId from AssignRoleCommand.
     * Returns Mono.empty() if no role exists with that id.
     */
    Mono<Role> findById(String roleId);

    /**
     * Load all roles in the system.
     * Used by: GET /roles — admin lists roles to know which ids to assign.
     */
    Flux<Role> findAll();
}
