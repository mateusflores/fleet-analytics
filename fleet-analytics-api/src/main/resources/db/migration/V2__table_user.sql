CREATE TABLE fleet.users
(
    id          UUID         NOT NULL,
    username    VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    is_enabled  BOOLEAN,
    created_at  timestamptz,
    last_update timestamptz,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE fleet.users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

ALTER TABLE fleet.users
    ADD CONSTRAINT uc_users_username UNIQUE (username);