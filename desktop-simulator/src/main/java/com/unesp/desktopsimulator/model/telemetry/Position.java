package com.unesp.desktopsimulator.model.telemetry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Position implements Serializable, RawData {

    private UUID vehicleId;
    private UUID tripId;
    private UUID driverId;
    private final Instant timestamp;
    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Double heading;
    private final Double accuracy;

    private Position(Builder builder) {
        timestamp = builder.timestamp;
        latitude = builder.latitude;
        longitude = builder.longitude;
        speedKmh = builder.speedKmh;
        heading = builder.heading;
        accuracy = builder.accuracy;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public Double getHeading() {
        return heading;
    }

    public Double getAccuracy() {
        return accuracy;
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
        private Double latitude;
        private Double longitude;
        private Double speedKmh;
        private Double heading;
        private Double accuracy;

        public Builder() {
        }

        public Builder timestamp(Instant val) {
            timestamp = val;
            return this;
        }

        public Builder latitude(Double val) {
            latitude = val;
            return this;
        }

        public Builder longitude(Double val) {
            longitude = val;
            return this;
        }

        public Builder speedKmh(Double val) {
            speedKmh = val;
            return this;
        }

        public Builder heading(Double val) {
            heading = val;
            return this;
        }

        public Builder accuracy(Double val) {
            accuracy = val;
            return this;
        }

        public Position build() {
            return new Position(this);
        }
    }
}
