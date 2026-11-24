package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateRoleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.PermissionSummary;
import com.unesp.fleetanalyticsapi.app.dtos.RoleSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.Permission;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @InjectMocks
    private RoleServiceImpl service;

    private static Permission perm(String name) {
        return Permission.builder().id(UUID.randomUUID()).name(name).build();
    }

    @Test
    void createWithValidPermissions() {
        Permission a = perm("vehicles:view");
        Permission b = perm("travel:start");
        when(permissionRepository.findAllById(Set.of(a.getId(), b.getId()))).thenReturn(List.of(a, b));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleSummary s = service.create(new CreateRoleRequest("Motorista", Set.of(a.getId(), b.getId())));

        assertThat(s.name()).isEqualTo("Motorista");
        assertThat(s.permissions()).extracting(PermissionSummary::name)
                .containsExactlyInAnyOrder("vehicles:view", "travel:start");
    }

    @Test
    void createRejectsUnknownPermission() {
        Permission a = perm("vehicles:view");
        UUID unknown = UUID.randomUUID();
        when(permissionRepository.findAllById(Set.of(a.getId(), unknown))).thenReturn(List.of(a));

        assertThatThrownBy(() -> service.create(new CreateRoleRequest("X", Set.of(a.getId(), unknown))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateReplacesNameAndPermissions() {
        UUID id = UUID.randomUUID();
        Role role = Role.builder().id(id).name("Old").permissions(new HashSet<>(Set.of(perm("a")))).build();
        Permission b = perm("b");
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        when(permissionRepository.findAllById(Set.of(b.getId()))).thenReturn(List.of(b));
        when(roleRepository.save(role)).thenReturn(role);

        RoleSummary s = service.update(id, new CreateRoleRequest("New", Set.of(b.getId())));

        assertThat(s.name()).isEqualTo("New");
        assertThat(s.permissions()).extracting(PermissionSummary::name).containsExactly("b");
    }

    @Test
    void updateAndDeleteOfUnknownRoleAreNotFound() {
        UUID id = UUID.randomUUID();
        when(roleRepository.findById(id)).thenReturn(Optional.empty());
        when(roleRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.update(id, new CreateRoleRequest("X", Set.of())))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(EntityNotFoundException.class);
        verify(roleRepository, never()).deleteById(any());
    }
}
