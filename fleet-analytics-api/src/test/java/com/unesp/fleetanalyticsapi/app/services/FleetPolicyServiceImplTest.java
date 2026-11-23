package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.FleetPolicySummary;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateFleetPolicyRequest;
import com.unesp.fleetanalyticsapi.model.entities.crud.FleetPolicy;
import com.unesp.fleetanalyticsapi.model.entities.crud.VehicleTypePolicy;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import com.unesp.fleetanalyticsapi.model.repositories.FleetPolicyRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleTypePolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetPolicyServiceImplTest {

    @Mock
    private FleetPolicyRepository policyRepository;

    @Mock
    private VehicleTypePolicyRepository vehicleTypeRepository;

    @InjectMocks
    private FleetPolicyServiceImpl service;

    private static FleetPolicy policy() {
        return FleetPolicy.builder().id(FleetPolicy.SINGLETON_ID).speedLimitKmh(90.0).harshAccelMs2(0.7)
                .harshBrakeMs2(1.5).idleThresholdS(300).fuelPricePerLiter(new BigDecimal("6.20"))
                .updatedAt(ZonedDateTime.now()).build();
    }

    @Test
    void getReturnsPolicyWithVehicleTypes() {
        when(policyRepository.findById(FleetPolicy.SINGLETON_ID)).thenReturn(Optional.of(policy()));
        when(vehicleTypeRepository.findAll()).thenReturn(List.of(
                VehicleTypePolicy.builder().vehicleType(VehicleType.RIGID_TRUCK).economyRpmMax(2000).build()));

        FleetPolicySummary s = service.get();

        assertThat(s.speedLimitKmh()).isEqualTo(90.0);
        assertThat(s.vehicleTypes()).extracting(FleetPolicySummary.VehicleTypeRpm::economyRpmMax).containsExactly(2000);
    }

    @Test
    void updateChangesValuesAndRecordsWhoChanged() {
        FleetPolicy p = policy();
        UUID user = UUID.randomUUID();
        when(policyRepository.findById(FleetPolicy.SINGLETON_ID)).thenReturn(Optional.of(p));
        when(vehicleTypeRepository.findById(VehicleType.ARTICULATED_VEHICLE)).thenReturn(Optional.empty());

        service.update(new UpdateFleetPolicyRequest(80.0, 0.8, 1.6, 240, new BigDecimal("6.35"),
                List.of(new UpdateFleetPolicyRequest.VehicleTypeRpm(VehicleType.ARTICULATED_VEHICLE, 1550))), user);

        assertThat(p.getSpeedLimitKmh()).isEqualTo(80.0);
        assertThat(p.getIdleThresholdS()).isEqualTo(240);
        assertThat(p.getUpdatedBy()).isEqualTo(user);
        ArgumentCaptor<VehicleTypePolicy> saved = ArgumentCaptor.forClass(VehicleTypePolicy.class);
        verify(vehicleTypeRepository).save(saved.capture());
        assertThat(saved.getValue().getVehicleType()).isEqualTo(VehicleType.ARTICULATED_VEHICLE);
        assertThat(saved.getValue().getEconomyRpmMax()).isEqualTo(1550);
    }

    @Test
    void updateWithoutVehicleTypesKeepsThem() {
        when(policyRepository.findById(FleetPolicy.SINGLETON_ID)).thenReturn(Optional.of(policy()));

        service.update(new UpdateFleetPolicyRequest(80.0, 0.8, 1.6, 240, new BigDecimal("6.35"), null), UUID.randomUUID());

        verify(vehicleTypeRepository, never()).save(any());
    }

    @Test
    void missingPolicyRowIsReported() {
        when(policyRepository.findById(FleetPolicy.SINGLETON_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(service::get).isInstanceOf(EntityNotFoundException.class);
    }
}
