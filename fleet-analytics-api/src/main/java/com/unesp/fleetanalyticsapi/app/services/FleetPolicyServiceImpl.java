package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.FleetPolicySummary;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateFleetPolicyRequest;
import com.unesp.fleetanalyticsapi.model.entities.crud.FleetPolicy;
import com.unesp.fleetanalyticsapi.model.entities.crud.VehicleTypePolicy;
import com.unesp.fleetanalyticsapi.model.repositories.FleetPolicyRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleTypePolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FleetPolicyServiceImpl implements FleetPolicyService {

    private final FleetPolicyRepository policyRepository;
    private final VehicleTypePolicyRepository vehicleTypeRepository;

    @Override
    @Transactional(readOnly = true)
    public FleetPolicySummary get() {
        return toSummary(load());
    }

    @Override
    @Transactional
    public FleetPolicySummary update(UpdateFleetPolicyRequest request, UUID updatedBy) {
        ZonedDateTime now = ZonedDateTime.now();
        FleetPolicy policy = load();
        policy.setSpeedLimitKmh(request.speedLimitKmh());
        policy.setHarshAccelMs2(request.harshAccelMs2());
        policy.setHarshBrakeMs2(request.harshBrakeMs2());
        policy.setIdleThresholdS(request.idleThresholdS());
        policy.setFuelPricePerLiter(request.fuelPricePerLiter());
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(updatedBy);
        policyRepository.save(policy);

        if (request.vehicleTypes() != null) {
            for (UpdateFleetPolicyRequest.VehicleTypeRpm vt : request.vehicleTypes()) {
                VehicleTypePolicy entity = vehicleTypeRepository.findById(vt.vehicleType())
                        .orElseGet(() -> VehicleTypePolicy.builder().vehicleType(vt.vehicleType()).build());
                entity.setEconomyRpmMax(vt.economyRpmMax());
                entity.setUpdatedAt(now);
                vehicleTypeRepository.save(entity);
            }
        }
        return toSummary(policy);
    }

    private FleetPolicy load() {
        return policyRepository.findById(FleetPolicy.SINGLETON_ID)
                .orElseThrow(() -> new EntityNotFoundException("Fleet policy not initialized (migration V13)"));
    }

    private FleetPolicySummary toSummary(FleetPolicy p) {
        var types = vehicleTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(v -> v.getVehicleType().name()))
                .map(v -> new FleetPolicySummary.VehicleTypeRpm(v.getVehicleType(), v.getEconomyRpmMax()))
                .toList();
        return new FleetPolicySummary(p.getSpeedLimitKmh(), p.getHarshAccelMs2(), p.getHarshBrakeMs2(),
                p.getIdleThresholdS(), p.getFuelPricePerLiter(), types, p.getUpdatedAt(), p.getUpdatedBy());
    }
}
