CREATE TABLE account(
    id UUID PRIMARY KEY,
    account_number VARCHAR(10) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_account_balance_non_negative CHECK (balance_minor >= 0),
    CONSTRAINT chk_account_currency CHECK (currency IN('NGN', 'USD')),
    CONSTRAINT chk_account_status CHECK(status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_account_customer_id ON account (customer_id);