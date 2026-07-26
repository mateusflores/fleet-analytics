package com.unesp.fleetanalyticsapi.model.repositories.specifications;

import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class VehicleSpecification {

    private VehicleSpecification() {
    }

    public static Specification<Vehicle> filter(
            String serialNumber, String licensePlate, String brand,
            String model, Integer year, String color, String nickname,
            VehicleType vehicleType, FuelType fuelType, String renavam
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (serialNumber != null && !serialNumber.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("serialNumber")),
                        "%" + serialNumber.toLowerCase() + "%"));
            }

            if (licensePlate != null && !licensePlate.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("licensePlate")),
                        "%" + licensePlate.toLowerCase() + "%"));
            }

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("brand")),
                        "%" + brand.toLowerCase() + "%"));
            }

            if (model != null && !model.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("model")),
                        "%" + model.toLowerCase() + "%"));
            }

            if (year != null) {
                predicates.add(cb.equal(root.get("year"), year));
            }

            if (color != null && !color.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("color")),
                        "%" + color.toLowerCase() + "%"));
            }

            if (nickname != null && !nickname.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nickname")),
                        "%" + nickname.toLowerCase() + "%"));
            }

            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("vehicleType"), vehicleType));
            }

            if (fuelType != null) {
                predicates.add(cb.equal(root.get("fuelType"), fuelType));
            }

            if (renavam != null && !renavam.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("renavam")),
                        "%" + renavam.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
