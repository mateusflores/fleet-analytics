package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.Role;
import org.springframework.data.jpa.domain.Specification;

public class RoleSpecification {

    private RoleSpecification() {
    }

    public static Specification<Role> nameLike(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%");
        };
    }
}
