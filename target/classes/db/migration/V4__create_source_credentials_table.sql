CREATE TABLE source_credentials (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    source_id     UUID         NOT NULL,
    source_type   VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_source_credentials_source FOREIGN KEY (source_id) REFERENCES sources(id)
);

CREATE INDEX idx_source_credentials_username ON source_credentials(username);
