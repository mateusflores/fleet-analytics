package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.crud.Trip;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TripSpecification {

    private TripSpecification() {
    }

    public static Specification<Trip> filter(
            UUID vehicleId, UUID driverId, Instant startAt, Instant endAt,
            Boolean ongoingTrip
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            }

            if (driverId != null) {
                predicates.add(cb.equal(root.get("driver").get("id"), driverId));
            }

            if (startAt != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), startAt));
            }

            if (endAt != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endTime"), endAt));
            }

            if (ongoingTrip != null) {
                predicates.add(Boolean.TRUE.equals(ongoingTrip)
                        ? cb.isNull(root.get("endTime"))
                        : cb.isNotNull(root.get("endTime")));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
