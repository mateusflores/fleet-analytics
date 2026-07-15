CREATE TABLE fleet.trips
(
    id         UUID NOT NULL,
    driver_id  UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ,
    CONSTRAINT pk_trips PRIMARY KEY (id),
    CONSTRAINT fk_trips_driver FOREIGN KEY (driver_id) REFERENCES fleet.users (id),
    CONSTRAINT fk_trips_vehicle FOREIGN KEY (vehicle_id) REFERENCES fleet.vehicles (id)
);

CREATE UNIQUE INDEX idx_trip_active_vehicle
    ON fleet.trips (vehicle_id) WHERE end_time IS NULL;
