-- Mini bank initial schema

CREATE TABLE clients (
    id                          UUID PRIMARY KEY,
    full_name                   VARCHAR(255)    NOT NULL,
    phone                       VARCHAR(20)     NOT NULL,
    passport_data               VARCHAR(255)    NOT NULL,
    status                      VARCHAR(30)     NOT NULL,
    registration_code           VARCHAR(10),
    registration_code_expires_at TIMESTAMP,
    created_at                  TIMESTAMP       NOT NULL,
    CONSTRAINT uq_clients_phone UNIQUE (phone)
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    client_id       UUID            NOT NULL REFERENCES clients (id),
    account_number  VARCHAR(34)     NOT NULL,
    balance         NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    held_amount     NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'RUB',
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number)
);

CREATE INDEX idx_accounts_client_id ON accounts (client_id);

CREATE TABLE cards (
    id              UUID PRIMARY KEY,
    account_id      UUID            NOT NULL REFERENCES accounts (id),
    card_number     VARCHAR(20)     NOT NULL,
    expiry_date     DATE            NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    CONSTRAINT uq_cards_card_number UNIQUE (card_number)
);

CREATE INDEX idx_cards_account_id ON cards (account_id);

CREATE TABLE sessions (
    id                  UUID PRIMARY KEY,
    client_id           UUID            NOT NULL REFERENCES clients (id),
    phone               VARCHAR(20)     NOT NULL,
    verification_code   VARCHAR(10)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    token               UUID,
    created_at          TIMESTAMP       NOT NULL,
    expires_at          TIMESTAMP       NOT NULL,
    CONSTRAINT uq_sessions_token UNIQUE (token)
);

CREATE INDEX idx_sessions_client_id ON sessions (client_id);

CREATE TABLE payment_templates (
    id                  UUID PRIMARY KEY,
    client_id           UUID            NOT NULL REFERENCES clients (id),
    name                VARCHAR(255)    NOT NULL,
    recipient_details   VARCHAR(500)    NOT NULL,
    default_amount      NUMERIC(19, 2),
    category            VARCHAR(50)     NOT NULL
);

CREATE INDEX idx_payment_templates_client_id ON payment_templates (client_id);

CREATE TABLE transactions (
    id                          UUID PRIMARY KEY,
    type                        VARCHAR(30)     NOT NULL,
    client_id                   UUID            NOT NULL REFERENCES clients (id),
    from_account_id             UUID            NOT NULL REFERENCES accounts (id),
    to_account_id               UUID            REFERENCES accounts (id),
    external_recipient_details  VARCHAR(1000),
    payment_template_id         UUID            REFERENCES payment_templates (id),
    amount                      NUMERIC(19, 2)  NOT NULL,
    commission                  NUMERIC(19, 2)  NOT NULL DEFAULT 0,
    confirmation_code           VARCHAR(10)     NOT NULL,
    status                      VARCHAR(20)     NOT NULL,
    idempotency_key             VARCHAR(255),
    failure_reason              VARCHAR(500),
    created_at                  TIMESTAMP       NOT NULL,
    updated_at                  TIMESTAMP       NOT NULL,
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_transactions_from_account_id ON transactions (from_account_id);
CREATE INDEX idx_transactions_client_id ON transactions (client_id);
CREATE INDEX idx_transactions_status ON transactions (status);

CREATE TABLE transaction_events (
    id              UUID PRIMARY KEY,
    transaction_id  UUID            NOT NULL REFERENCES transactions (id),
    step            VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    payload         VARCHAR(1000),
    created_at      TIMESTAMP       NOT NULL
);

CREATE INDEX idx_transaction_events_transaction_id ON transaction_events (transaction_id);
