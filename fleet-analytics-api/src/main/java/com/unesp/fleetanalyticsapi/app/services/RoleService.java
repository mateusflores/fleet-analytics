package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateRoleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RoleSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RoleService {

    List<RoleSummary> findAll();

    RoleSummary findById(UUID id);

    RoleSummary create(CreateRoleRequest request);

    RoleSummary update(UUID id, CreateRoleRequest request);

    void delete(UUID id);

    Page<RoleSummary> search(String name, Pageable pageable);
}
