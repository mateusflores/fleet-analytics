CREATE TABLE fleet.vehicles
(
    id            UUID         NOT NULL,
    serial_number VARCHAR(255),
    license_plate VARCHAR(255),
    brand         VARCHAR(255),
    model         VARCHAR(255),
    year          INTEGER,
    color         VARCHAR(255),
    mileage_km    numeric(16, 3),
    vehicle_type  VARCHAR(255) NOT NULL,
    max_load_kg   DECIMAL,
    fuel_type     VARCHAR(255),
    nickname      VARCHAR(255),
    renavam       VARCHAR(255),
    is_active     BOOLEAN      NOT NULL,
    created_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at    TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_vehicles PRIMARY KEY (id)
);

ALTER TABLE fleet.vehicles
    ADD CONSTRAINT uc_vehicles_license_plate UNIQUE (license_plate);

ALTER TABLE fleet.vehicles
    ADD CONSTRAINT uc_vehicles_serial_number UNIQUE (serial_number);