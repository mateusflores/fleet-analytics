package com.unesp.desktopsimulator.model.telemetry;

import java.util.UUID;

public interface RawData {

    UUID getVehicleId();

    void setVehicleId(UUID vehicleId);

    UUID getTripId();

    void setTripId(UUID tripId);

    UUID getDriverId();

    void setDriverId(UUID driverId);

}
