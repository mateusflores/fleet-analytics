package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.telemetry.Insight;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class InsightSpecification {

    private InsightSpecification() {
    }

    public static Specification<Insight> filter(Collection<InsightStatus> statuses, InsightScope scope,
                                                UUID subjectId, String category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (scope != null) {
                predicates.add(cb.equal(root.get("scope"), scope));
            }
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
