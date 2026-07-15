CREATE TABLE fleet.permissions
(
    id          UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT pk_permissions PRIMARY KEY (id)
);

CREATE TABLE fleet.refresh_tokens
(
    id         UUID                        NOT NULL,
    token      VARCHAR(512)                NOT NULL,
    user_id    UUID                        NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revoked    BOOLEAN                     NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

CREATE TABLE fleet.role_permissions
(
    permission_id UUID NOT NULL,
    role_id       UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (permission_id, role_id)
);

CREATE TABLE fleet.roles
(
    id   UUID         NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id)
);

CREATE TABLE fleet.users
(
    id          UUID         NOT NULL,
    username    VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    is_enabled  BOOLEAN,
    created_at  TIMESTAMP WITHOUT TIME ZONE,
    last_update TIMESTAMP WITHOUT TIME ZONE,
    role_id     UUID,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE fleet.permissions
    ADD CONSTRAINT uc_permissions_name UNIQUE (name);

ALTER TABLE fleet.refresh_tokens
    ADD CONSTRAINT uc_refresh_tokens_token UNIQUE (token);

ALTER TABLE fleet.roles
    ADD CONSTRAINT uc_roles_name UNIQUE (name);

ALTER TABLE fleet.users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

ALTER TABLE fleet.users
    ADD CONSTRAINT uc_users_username UNIQUE (username);

ALTER TABLE fleet.refresh_tokens
    ADD CONSTRAINT FK_REFRESH_TOKENS_ON_USER FOREIGN KEY (user_id) REFERENCES fleet.users (id);

ALTER TABLE fleet.users
    ADD CONSTRAINT FK_USERS_ON_ROLE FOREIGN KEY (role_id) REFERENCES fleet.roles (id);

ALTER TABLE fleet.role_permissions
    ADD CONSTRAINT fk_rolper_on_permission FOREIGN KEY (permission_id) REFERENCES fleet.permissions (id);

ALTER TABLE fleet.role_permissions
    ADD CONSTRAINT fk_rolper_on_role FOREIGN KEY (role_id) REFERENCES fleet.roles (id);