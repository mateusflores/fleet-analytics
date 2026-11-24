package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateRoleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.PermissionSummary;
import com.unesp.fleetanalyticsapi.app.dtos.RoleSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.Permission;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.specifications.RoleSpecification;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Override
    public List<RoleSummary> findAll() {
        return roleRepository.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public RoleSummary findById(UUID id) {
        return roleRepository.findById(id)
                .map(this::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
    }

    @Override
    public RoleSummary create(CreateRoleRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new EntityExistsException("Role already exists: " + request.name());
        }
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.permissionIds()));

        if (permissions.size() != request.permissionIds().size()) {
            throw new IllegalArgumentException("One or more permission IDs are invalid");
        }

        Role role = Role.builder()
                .name(request.name())
                .permissions(permissions)
                .build();

        role = roleRepository.save(role);
        return toSummary(role);
    }

    @Override
    public RoleSummary update(UUID id, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.permissionIds()));

        if (permissions.size() != request.permissionIds().size()) {
            throw new IllegalArgumentException("One or more permission IDs are invalid");
        }

        roleRepository.findByName(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new EntityExistsException("Role already exists: " + request.name());
                });
        role.setName(request.name());
        role.setPermissions(permissions);

        role = roleRepository.save(role);
        return toSummary(role);
    }

    @Override
    public Page<RoleSummary> search(String name, Pageable pageable) {
        return roleRepository.findAll(RoleSpecification.nameLike(name), pageable)
                .map(this::toSummary);
    }

    @Override
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Role not found: " + id);
        }
        if (userRepository.existsByRoleId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role is assigned to users and cannot be deleted");
        }
        roleRepository.deleteById(id);
    }

    private RoleSummary toSummary(Role role) {
        Set<PermissionSummary> permSummaries = role.getPermissions().stream()
                .map(p -> new PermissionSummary(p.getId(), p.getName(), p.getDescription()))
                .collect(Collectors.toSet());

        return new RoleSummary(role.getId(), role.getName(), permSummaries);
    }
}
