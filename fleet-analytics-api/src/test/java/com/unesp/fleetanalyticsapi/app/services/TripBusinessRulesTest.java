package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.repositories.TripRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de negócio novas: viagem não começa com veículo inativo ou motorista desativado; atualização parcial. */
@ExtendWith(MockitoExtension.class)
class TripBusinessRulesTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @InjectMocks
    private TripServiceImpl service;

    private void stub(boolean vehicleActive, boolean driverEnabled, UUID d, UUID v) {
        when(userRepository.findById(d)).thenReturn(Optional.of(User.builder().id(d).enabled(driverEnabled).build()));
        when(vehicleRepository.findById(v)).thenReturn(Optional.of(Vehicle.builder().id(v).active(vehicleActive).build()));
    }

    @Test
    void inactiveVehicleCannotStartTrip() {
        UUID d = UUID.randomUUID(), v = UUID.randomUUID();
        stub(false, true, d, v);
        assertThatThrownBy(() -> service.startTrip(new CreateTripRequest(d, v)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("inactive")
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(tripRepository, never()).save(any());
    }

    @Test
    void disabledDriverCannotStartTrip() {
        UUID d = UUID.randomUUID(), v = UUID.randomUUID();
        stub(true, false, d, v);
        assertThatThrownBy(() -> service.startTrip(new CreateTripRequest(d, v)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("disabled");
        verify(tripRepository, never()).save(any());
    }

    @Test
    void userWithNullEnabledFlagIsTreatedAsDisabled() {
        assertThat(User.builder().build().isEnabled()).isFalse();
        assertThat(User.builder().enabled(true).build().isEnabled()).isTrue();
    }

    @Test
    void partialVehicleUpdateKeepsActiveFlag() {
        VehicleRepository repo = mock(VehicleRepository.class);
        VehicleServiceImpl vehicles = new VehicleServiceImpl(repo);
        UUID id = UUID.randomUUID();
        Vehicle v = Vehicle.builder().id(id).licensePlate("ABC1D23").active(true).build();
        when(repo.findById(id)).thenReturn(Optional.of(v));
        when(repo.save(v)).thenReturn(v);

        vehicles.update(id, new UpdateVehicleRequest(null, null, null, null, null, "azul", null, null, null, null,
                null, null, null));

        assertThat(v.getActive()).isTrue();
        assertThat(v.getColor()).isEqualTo("azul");
    }
}
