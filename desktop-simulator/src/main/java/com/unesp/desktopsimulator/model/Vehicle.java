package com.unesp.desktopsimulator.model;

import java.util.UUID;

public class Vehicle {

    private final UUID id;
    private final String nickname;
    private final String licensePlate;
    private final String brand;
    private final String model;
    private final Integer year;
    private final String color;
    private final String vehicleType;

    private Driver drivenBy = null;

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Integer getYear() {
        return year;
    }

    public String getColor() {
        return color;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public Driver getDrivenBy() {
        return drivenBy;
    }

    public void setDrivenBy(Driver drivenBy) {
        this.drivenBy = drivenBy;
    }

    private Vehicle(Builder builder) {
        id = builder.id;
        nickname = builder.nickname;
        licensePlate = builder.licensePlate;
        brand = builder.brand;
        model = builder.model;
        year = builder.year;
        color = builder.color;
        vehicleType = builder.vehicleType;
    }

    public static final class Builder {
        private UUID id;
        private String nickname;
        private String licensePlate;
        private String brand;
        private String model;
        private Integer year;
        private String color;
        private String vehicleType;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withId(UUID val) {
            id = val;
            return this;
        }

        public Builder withNickname(String val) {
            nickname = val;
            return this;
        }

        public Builder withLicensePlate(String val) {
            licensePlate = val;
            return this;
        }

        public Builder withBrand(String val) {
            brand = val;
            return this;
        }

        public Builder withModel(String val) {
            model = val;
            return this;
        }

        public Builder withYear(Integer val) {
            year = val;
            return this;
        }

        public Builder withColor(String val) {
            color = val;
            return this;
        }

        public Builder withVehicleType(String val) {
            vehicleType = val;
            return this;
        }

        public Vehicle build() {
            return new Vehicle(this);
        }
    }
}
