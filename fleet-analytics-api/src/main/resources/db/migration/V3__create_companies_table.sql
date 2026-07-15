CREATE TABLE fleet.addresses
(
    id                         UUID       NOT NULL,
    address_line               VARCHAR(255),
    number                     VARCHAR(30),
    neighborhood               VARCHAR(255),
    additional_address_details VARCHAR(255),
    cep                        VARCHAR(255),
    city                       VARCHAR(255),
    state_code                 VARCHAR(2) NOT NULL,
    country                    VARCHAR(255),
    created_at                 TIMESTAMP WITHOUT TIME ZONE,
    updated_at                 TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_addresses PRIMARY KEY (id)
);

CREATE TABLE fleet.companies
(
    id             UUID        NOT NULL,
    corporate_name VARCHAR(255),
    trade_name     VARCHAR(255),
    cnpj           VARCHAR(14) NOT NULL,
    address_id     UUID,
    CONSTRAINT pk_companies PRIMARY KEY (id)
);

ALTER TABLE fleet.companies
    ADD CONSTRAINT uc_companies_address UNIQUE (address_id);

ALTER TABLE fleet.companies
    ADD CONSTRAINT uc_companies_cnpj UNIQUE (cnpj);

ALTER TABLE fleet.companies
    ADD CONSTRAINT FK_COMPANIES_ON_ADDRESS FOREIGN KEY (address_id) REFERENCES fleet.addresses (id);