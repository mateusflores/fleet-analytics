package com.unesp.fleetanalyticsapi.infra.config;

import com.unesp.fleetanalyticsapi.model.entities.Permission;
import com.unesp.fleetanalyticsapi.model.entities.Role;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        if (permissionRepository.count() > 0) {
            return;
        }

        List<Permission> allPermissions = permissionRepository.saveAll(List.of(
                Permission.builder().name("users:create").description("Create new users").build(),
                Permission.builder().name("users:read").description("View user details").build(),
                Permission.builder().name("users:disable").description("Disable user accounts").build(),
                Permission.builder().name("analytics:view").description("View analytics and reports").build(),
                Permission.builder().name("travel:start").description("Start a travel").build(),
                Permission.builder().name("travel:read").description("View travel details").build(),
                Permission.builder().name("drivers:view").description("View driver list and details").build(),
                Permission.builder().name("drivers:create").description("Create new drivers").build(),
                Permission.builder().name("drivers:update").description("Update driver information").build(),
                Permission.builder().name("vehicles:view").description("View vehicle list and details").build(),
                Permission.builder().name("vehicles:create").description("Create new vehicles").build(),
                Permission.builder().name("vehicles:update").description("Update vehicle information").build(),
                Permission.builder().name("roles:create").description("Create new roles").build(),
                Permission.builder().name("roles:read").description("View roles and permissions").build(),
                Permission.builder().name("roles:update").description("Update roles and permissions").build(),
                Permission.builder().name("roles:delete").description("Delete roles").build()
        ));

        Map<String, Permission> permMap = allPermissions.stream()
                .collect(Collectors.toMap(Permission::getName, p -> p));

        roleRepository.saveAll(List.of(
                Role.builder()
                        .name("Admin")
                        .permissions(Set.copyOf(permMap.values()))
                        .build(),
                Role.builder()
                        .name("Analyst")
                        .permissions(Set.of(
                                permMap.get("analytics:view"),
                                permMap.get("drivers:view"),
                                permMap.get("vehicles:view"),
                                permMap.get("travel:read")
                        ))
                        .build(),
                Role.builder()
                        .name("Driver")
                        .permissions(Set.of(
                                permMap.get("travel:start"),
                                permMap.get("travel:read"),
                                permMap.get("vehicles:view")
                        ))
                        .build()
        ));
    }
}
