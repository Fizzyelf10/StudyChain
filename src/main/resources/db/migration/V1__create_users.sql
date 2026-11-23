CREATE TABLE users (
    id                      UUID PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(100) NOT NULL,
    display_name            VARCHAR(60)  NOT NULL,
    role                    VARCHAR(10)  NOT NULL,
    wallet_address          VARCHAR(42)  UNIQUE,
    wallet_nonce            VARCHAR(64),
    wallet_nonce_expires_at TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL
);
