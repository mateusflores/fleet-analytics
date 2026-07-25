package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    private UserSpecification() {
    }

    public static Specification<User> filter(
            String firstName, String lastName, String email,
            String username, String roleName
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (firstName != null && !firstName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("firstName")),
                        "%" + firstName.toLowerCase() + "%"));
            }

            if (lastName != null && !lastName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("lastName")),
                        "%" + lastName.toLowerCase() + "%"));
            }

            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + email.toLowerCase() + "%"));
            }

            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")),
                        "%" + username.toLowerCase() + "%"));
            }

            if (roleName != null && !roleName.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("role").get("name")),
                        roleName.toLowerCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
