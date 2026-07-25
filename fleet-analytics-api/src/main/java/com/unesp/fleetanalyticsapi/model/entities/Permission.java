package com.unesp.fleetanalyticsapi.model.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission implements GrantedAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Override
    public @Nullable String getAuthority() {
        return name;
    }
}
