package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.crud.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {
}
