package com.unesp.fleetanalyticsapi.infra.config;

import com.unesp.fleetanalyticsapi.model.entities.Permission;
import com.unesp.fleetanalyticsapi.model.entities.Role;
import com.unesp.fleetanalyticsapi.model.entities.User;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.RoleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
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
    @SuppressWarnings("unchecked")
    public void run(String... args) {
        if (permissionRepository.count() > 0) {
            return;
        }

        Yaml yaml = new Yaml();
        InputStream input = getClass().getResourceAsStream("/seed/seed.yaml");
        Map<String, Object> seed = yaml.load(input);

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
}
