package com.unesp.desktopsimulator.model;

import java.util.UUID;

public class Driver {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private boolean isAvailable = true;

    private Driver(Builder builder) {
        id = builder.id;
        firstName = builder.firstName;
        lastName = builder.lastName;
        setAvailable(builder.isAvailable);
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean getAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }


    public static final class Builder {
        private final UUID id;
        private final String firstName;
        private final String lastName;
        private boolean isAvailable;

        public Builder(UUID id, String firstName, String lastName) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public Builder isAvailable(boolean val) {
            isAvailable = val;
            return this;
        }

        public Driver build() {
            return new Driver(this);
        }
    }
}
