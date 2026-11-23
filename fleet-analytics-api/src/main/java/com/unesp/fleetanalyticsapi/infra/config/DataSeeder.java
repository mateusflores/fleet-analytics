package com.unesp.fleetanalyticsapi.infra.config;

import com.unesp.fleetanalyticsapi.model.entities.crud.Permission;
import com.unesp.fleetanalyticsapi.model.entities.crud.Role;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void run(String... args) {
        Yaml yaml = new Yaml();
        InputStream input = getClass().getResourceAsStream("/seed/seed.yaml");
        Map<String, Object> seed = yaml.load(input);

        if (permissionRepository.count() > 0) {
            syncNewPermissions(seed);
            return;
        }

        List<Map<String, String>> permConfigs = (List<Map<String, String>>) seed.get("permissions");
        List<Permission> savedPermissions = permissionRepository.saveAll(
                permConfigs.stream()
                        .map(p -> Permission.builder()
                                .name(p.get("name"))
                                .description(p.get("description"))
                                .build())
                        .toList()
        );

        Map<String, Permission> permMap = savedPermissions.stream()
                .collect(Collectors.toMap(Permission::getName, p -> p));

        List<Map<String, Object>> roleConfigs = (List<Map<String, Object>>) seed.get("roles");
        for (Map<String, Object> rc : roleConfigs) {
            String roleName = (String) rc.get("name");
            Object permValue = rc.get("permissions");
            Set<Permission> rolePermissions;

            if ("*".equals(permValue)) {
                rolePermissions = Set.copyOf(savedPermissions);
            } else {
                List<String> permNames = (List<String>) permValue;
                rolePermissions = permNames.stream().map(permMap::get).collect(Collectors.toSet());
            }

            roleRepository.save(
                    Role.builder().name(roleName).permissions(rolePermissions).build()
            );
        }

        Map<String, Object> adminConfig = (Map<String, Object>) seed.get("admin");
        String adminUsername = (String) adminConfig.get("username");
        String adminEmail = (String) adminConfig.get("email");
        String adminRoleName = (String) adminConfig.get("roleName");
        String passwordEnvVar = (String) adminConfig.get("passwordEnvVar");

        if (userRepository.findByUsername(adminUsername).isEmpty()) {
            String adminPassword = env.getProperty(passwordEnvVar);
            if (adminPassword == null || adminPassword.isBlank()) {
                throw new IllegalStateException(
                        "Environment variable '" + passwordEnvVar + "' is required but not set");
            }

            Role adminRole = roleRepository.findByName(adminRoleName)
                    .orElseThrow(() -> new IllegalStateException("Admin role not found"));

            userRepository.save(User.builder()
                    .username(adminUsername)
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .enabled(true)
                    .role(adminRole)
                    .build());
        }
    }

    /**
     * Banco já inicializado: cria as permissões do seed que ainda não existem (adicionadas em versões
     * posteriores, ex.: fleet-policy:update) e as concede aos perfis configurados com "*".
     */
    @SuppressWarnings("unchecked")
    private void syncNewPermissions(Map<String, Object> seed) {
        Set<String> existing = permissionRepository.findAll().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
        List<Map<String, String>> permConfigs = (List<Map<String, String>>) seed.get("permissions");
        List<Permission> added = permissionRepository.saveAll(permConfigs.stream()
                .filter(p -> !existing.contains(p.get("name")))
                .map(p -> Permission.builder().name(p.get("name")).description(p.get("description")).build())
                .toList());
        if (added.isEmpty()) {
            return;
        }
        List<Map<String, Object>> roleConfigs = (List<Map<String, Object>>) seed.get("roles");
        for (Map<String, Object> rc : roleConfigs) {
            if (!"*".equals(rc.get("permissions"))) continue;
            roleRepository.findByName((String) rc.get("name")).ifPresent(role -> {
                Set<Permission> perms = new HashSet<>(role.getPermissions());
                perms.addAll(added);
                role.setPermissions(perms);
                roleRepository.save(role);
            });
        }
    }
}
