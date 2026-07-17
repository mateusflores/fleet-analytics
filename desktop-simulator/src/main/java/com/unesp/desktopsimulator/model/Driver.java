package com.unesp.desktopsimulator.model;

import java.util.UUID;

public class Driver {

    private final UUID id;
    private final String name;
    private boolean isAvailable = true;

    private Driver(Builder builder) {
        name = builder.name;
        id = builder.id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean getAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public static final class Builder {
        private String name;
        private UUID id;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withName(String val) {
            name = val;
            return this;
        }

        public Builder withId(UUID val) {
            id = val;
            return this;
        }

        public Driver build() {
            return new Driver(this);
        }
    }
}
