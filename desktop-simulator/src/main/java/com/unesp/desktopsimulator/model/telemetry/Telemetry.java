package com.unesp.desktopsimulator.model.telemetry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Telemetry implements Serializable, RawData {
    private UUID vehicleId;
    private UUID tripId;
    private UUID driverId;
    private final Instant timestamp;
    private final Integer engineRpm;
    private final Double fuelLevelPct;
    private final Double engineTempC;
    private final Double odometerKm;
    private final Double batteryVoltage;

    private Telemetry(Builder builder) {
        timestamp = builder.timestamp;
        engineRpm = builder.engineRpm;
        fuelLevelPct = builder.fuelLevelPct;
        engineTempC = builder.engineTempC;
        odometerKm = builder.odometerKm;
        batteryVoltage = builder.batteryVoltage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Integer getEngineRpm() {
        return engineRpm;
    }

    public Double getFuelLevelPct() {
        return fuelLevelPct;
    }

    public Double getEngineTempC() {
        return engineTempC;
    }

    public Double getOdometerKm() {
        return odometerKm;
    }

    public Double getBatteryVoltage() {
        return batteryVoltage;
    }

    @Override
    public UUID getVehicleId() {
        return vehicleId;
    }

    @Override
    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    public UUID getTripId() {
        return tripId;
    }

    @Override
    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    @Override
    public UUID getDriverId() {
        return driverId;
    }

    @Override
    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public static final class Builder {
        private Instant timestamp;
        private Integer engineRpm;
        private Double fuelLevelPct;
        private Double engineTempC;
        private Double odometerKm;
        private Double batteryVoltage;

        public Builder() {
        }

        public Builder timestamp(Instant val) {
            timestamp = val;
            return this;
        }

        public Builder engineRpm(Integer val) {
            engineRpm = val;
            return this;
        }

        public Builder fuelLevelPct(Double val) {
            fuelLevelPct = val;
            return this;
        }

        public Builder engineTempC(Double val) {
            engineTempC = val;
            return this;
        }

        public Builder odometerKm(Double val) {
            odometerKm = val;
            return this;
        }

        public Builder batteryVoltage(Double val) {
            batteryVoltage = val;
            return this;
        }

        public Telemetry build() {
            return new Telemetry(this);
        }
    }
}
