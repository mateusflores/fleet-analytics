package com.unesp.desktopsimulator.model.telemetry;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Message implements Serializable, RawData {
    private UUID vehicleId;
    private UUID tripId;
    private UUID driverId;
    private final Instant timestamp;
    private final String eventType;
    private final String severity;
    private final String message;

    private Message(Builder builder) {
        timestamp = builder.timestamp;
        eventType = builder.eventType;
        severity = builder.severity;
        message = builder.message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
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
        private String eventType;
        private String severity;
        private String message;

        public Builder() {
        }

        public Builder timestamp(Instant val) {
            timestamp = val;
            return this;
        }

        public Builder eventType(String val) {
            eventType = val;
            return this;
        }

        public Builder severity(String val) {
            severity = val;
            return this;
        }

        public Builder message(String val) {
            message = val;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}
