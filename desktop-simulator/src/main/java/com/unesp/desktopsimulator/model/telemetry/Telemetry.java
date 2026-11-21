package com.unesp.desktopsimulator.model.telemetry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Leitura de telemetria publicada em {@code telemetry/telemetry}.
 *
 * <p>Os seis primeiros campos são os que a API persiste hoje ({@code TelemetryMessage}); os demais são sinais
 * J1939 adicionais usados pelo serviço de ML (a API ignora campos desconhecidos). Os nomes seguem o
 * {@code FIELD_MAP} de {@code ml-service/src/ml_service/inference/engine.py}.
 */
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
    private final Double speedKmh;
    private final Double loadPct;
    private final Double engineTorquePct;
    private final Double pedalPct;
    private final Integer gear;
    private final Double oilKpa;
    private final Double fuelRateLph;
    private final Double boostKpa;
    private final Double intakeC;

    private Telemetry(Builder builder) {
        timestamp = builder.timestamp;
        engineRpm = builder.engineRpm;
        fuelLevelPct = builder.fuelLevelPct;
        engineTempC = builder.engineTempC;
        odometerKm = builder.odometerKm;
        batteryVoltage = builder.batteryVoltage;
        speedKmh = builder.speedKmh;
        loadPct = builder.loadPct;
        engineTorquePct = builder.engineTorquePct;
        pedalPct = builder.pedalPct;
        gear = builder.gear;
        oilKpa = builder.oilKpa;
        fuelRateLph = builder.fuelRateLph;
        boostKpa = builder.boostKpa;
        intakeC = builder.intakeC;
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

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public Double getLoadPct() {
        return loadPct;
    }

    public Double getEngineTorquePct() {
        return engineTorquePct;
    }

    public Double getPedalPct() {
        return pedalPct;
    }

    public Integer getGear() {
        return gear;
    }

    public Double getOilKpa() {
        return oilKpa;
    }

    public Double getFuelRateLph() {
        return fuelRateLph;
    }

    public Double getBoostKpa() {
        return boostKpa;
    }

    public Double getIntakeC() {
        return intakeC;
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
        private Double speedKmh;
        private Double loadPct;
        private Double engineTorquePct;
        private Double pedalPct;
        private Integer gear;
        private Double oilKpa;
        private Double fuelRateLph;
        private Double boostKpa;
        private Double intakeC;

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

        public Builder speedKmh(Double val) {
            speedKmh = val;
            return this;
        }

        public Builder loadPct(Double val) {
            loadPct = val;
            return this;
        }

        public Builder engineTorquePct(Double val) {
            engineTorquePct = val;
            return this;
        }

        public Builder pedalPct(Double val) {
            pedalPct = val;
            return this;
        }

        public Builder gear(Integer val) {
            gear = val;
            return this;
        }

        public Builder oilKpa(Double val) {
            oilKpa = val;
            return this;
        }

        public Builder fuelRateLph(Double val) {
            fuelRateLph = val;
            return this;
        }

        public Builder boostKpa(Double val) {
            boostKpa = val;
            return this;
        }

        public Builder intakeC(Double val) {
            intakeC = val;
            return this;
        }

        public Telemetry build() {
            return new Telemetry(this);
        }
    }
}
