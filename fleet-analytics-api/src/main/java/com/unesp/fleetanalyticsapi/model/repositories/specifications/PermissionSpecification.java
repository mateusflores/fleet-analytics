package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.Permission;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PermissionSpecification {

    private PermissionSpecification() {
    }

    public static Specification<Permission> filter(String name, String description) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + name.toLowerCase() + "%"));
            }

            if (description != null && !description.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("description")),
                        "%" + description.toLowerCase() + "%"));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
