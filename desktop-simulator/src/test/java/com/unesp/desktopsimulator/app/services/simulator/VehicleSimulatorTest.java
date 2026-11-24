package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.mqtt.MqttPublisher;
import com.unesp.desktopsimulator.app.services.simulation.FaultSet;
import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.VehicleModel;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.RawData;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VehicleSimulatorTest {

    @Test
    void tickPublishesConsistentPositionAndTelemetry() {
        Vehicle vehicle = Vehicle.Builder.builder().withId(UUID.randomUUID()).withNickname("v1")
                .withVehicleType("RIGID_TRUCK").build();
        Driver driver = new Driver.Builder(UUID.randomUUID(), "Ana", "Silva").build();
        SimulationState state = new SimulationState(vehicle, driver);
        state.setTripId(UUID.randomUUID());
        VehicleModel model = new VehicleModel(VehicleProfile.MEDIUM_RIGID, new RouteCatalog().get("campinas-sao-paulo"),
                SimulationOptions.defaults(1), state.getFaults());
        MqttPublisher publisher = mock(MqttPublisher.class);
        VehicleSimulator sim = new VehicleSimulator(state, model, publisher, 1000);

        Instant t0 = Instant.parse("2026-11-21T12:00:00Z");
        for (int i = 0; i < 120; i++) sim.tick(t0.plusSeconds(i), 1.0);

        ArgumentCaptor<RawData> sent = ArgumentCaptor.forClass(RawData.class);
        verify(publisher, atLeast(240)).publish(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(d -> {
            assertThat(d.getVehicleId()).isEqualTo(vehicle.getId());
            assertThat(d.getTripId()).isEqualTo(state.getTripId());
            assertThat(d.getDriverId()).isEqualTo(driver.getId());
        });
        Position pos = state.getLastPosition();
        Telemetry tel = state.getLastTelemetry();
        assertThat(tel.getSpeedKmh()).isEqualTo(pos.getSpeedKmh());
        assertThat(tel.getTimestamp()).isEqualTo(pos.getTimestamp()).isEqualTo(t0.plusSeconds(119));
        assertThat(tel.getOilKpa()).isNotNull();
        assertThat(tel.getGear()).isNotNull();
    }
}
