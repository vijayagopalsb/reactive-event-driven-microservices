package com.reactiveevent.platform.auth.domain.repository;

import com.reactiveevent.platform.common.domain.permission.Permission;
import com.reactiveevent.platform.common.domain.user.UserId;
import reactor.core.publisher.Flux;

public interface PermissionRepository {

    /**
     * Load all permissions assigned to a given user.
     * RBAC: user → roles → permissions (many-to-many)
     */
    Flux<Permission> findPermissionsByUserId(UserId userId);
}
