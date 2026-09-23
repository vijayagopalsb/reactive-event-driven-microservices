package com.reactiveevent.platform.auth.api;

import com.reactiveevent.platform.auth.domain.repository.RoleRepository;
import com.reactiveevent.platform.auth.domain.repository.UserRepository;
import com.reactiveevent.platform.common.api.user.AssignRoleCommand;
import com.reactiveevent.platform.common.api.user.CreateUserCommand;
import com.reactiveevent.platform.common.api.user.RoleResponse;
import com.reactiveevent.platform.common.api.user.UserResponse;
import com.reactiveevent.platform.common.application.user.AssignRoleUseCase;
import com.reactiveevent.platform.common.application.user.CreateUserUseCase;
import com.reactiveevent.platform.common.domain.user.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * UserManagementController — admin-only endpoints for managing users and roles.
 *
 * Base path: /users
 *
 * All endpoints require ROLE_ADMIN, enforced by @PreAuthorize.
 * Spring Security reads the "roles" claim from the JWT and checks it
 * before the method body runs. No ROLE_ADMIN → 403 Forbidden immediately.
 *
 * Endpoints:
 *   POST /users              → create a new LOCAL user            → 201 Created
 *   GET  /users              → list all users with their roles    → 200 OK
 *   GET  /users/{id}         → get one user by id                 → 200 OK / 404
 *   POST /users/{id}/roles   → assign a role to a user           → 204 No Content
 *   GET  /roles              → list all roles (for role picker)   → 200 OK
 *
 * Controller responsibility:
 *   1. Parse HTTP input into commands
 *   2. Delegate to use cases / repositories
 *   3. Return the correct HTTP status + body
 *   No business logic here.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final CreateUserUseCase createUserUseCase;
    private final AssignRoleUseCase assignRoleUseCase;
    private final UserRepository    userRepository;
    private final RoleRepository    roleRepository;

    // -------------------------------------------------------------------------
    // POST /users
    // Admin creates a new LOCAL user.
    //
    // Request body:
    //   { "email": "john@example.com", "rawPassword": "Secret@123", "role": "USER" }
    //
    // Response 201 Created:
    //   { "id": "...", "email": "john@example.com", "status": "ACTIVE", "roles": ["USER"] }
    // -------------------------------------------------------------------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserResponse> createUser(@RequestBody CreateUserCommand command) {
        return createUserUseCase.createUser(command);
    }

    // -------------------------------------------------------------------------
    // GET /users
    // List all users with their assigned roles.
    //
    // For each user we load their roles reactively via flatMap.
    // Flux.flatMap runs the inner publishers concurrently — efficient for N users.
    // -------------------------------------------------------------------------
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserResponse> listUsers() {
        return userRepository.findAll()
                .flatMap(user ->
                        roleRepository.findRolesByUserId(user.getId())
                                .map(role -> role.getName())
                                .collectList()
                                .map(roleNames -> new UserResponse(
                                        user.getId().getValue(),
                                        user.getEmail(),
                                        user.getStatus(),
                                        roleNames
                                ))
                );
    }

    // -------------------------------------------------------------------------
    // GET /users/{id}
    // Get a single user by UUID.
    //
    // @PathVariable extracts {id} from the URL path.
    // defaultIfEmpty → returns 404 if no user found instead of empty body.
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<UserResponse>> getUser(@PathVariable UUID id) {
        return userRepository.findById(UserId.of(id))
                .flatMap(user ->
                        roleRepository.findRolesByUserId(user.getId())
                                .map(role -> role.getName())
                                .collectList()
                                .map(roleNames -> new UserResponse(
                                        user.getId().getValue(),
                                        user.getEmail(),
                                        user.getStatus(),
                                        roleNames
                                ))
                )
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
        // defaultIfEmpty fires when userRepository.findById returns Mono.empty()
        // It converts that empty into a 404 response — without it you'd get an empty 200
    }

    // -------------------------------------------------------------------------
    // POST /users/{id}/roles
    // Assign a role to a user.
    //
    // We derive userId from the URL path — not from the request body.
    // This is the RESTful convention: the resource being modified is in the URL.
    //
    // Request body: { "userId": "...", "roleId": "uuid-of-role" }
    // We override userId with the path variable to ensure consistency.
    //
    // Response 204 No Content — no body, just confirmation it worked.
    // -------------------------------------------------------------------------
    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> assignRole(@PathVariable UUID id,
                                 @RequestBody AssignRoleCommand command) {
        return assignRoleUseCase.assignRole(
                new AssignRoleCommand(id, command.roleId())
        );
    }

    // -------------------------------------------------------------------------
    // GET /roles
    // List all roles available in the system.
    // Admin calls this first to get role UUIDs, then uses them in POST /{id}/roles.
    //
    // Note: mapped under /users/roles (not /roles) because this controller
    // owns /users/**. A dedicated /roles prefix would need a separate controller.
    // -------------------------------------------------------------------------
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<RoleResponse> listRoles() {
        return roleRepository.findAll()
                .map(role -> new RoleResponse(
                        role.getId().getValue(),
                        role.getName()
                ));
    }
}
