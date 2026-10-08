CREATE TABLE ledger_transaction (
                                    id           UUID         PRIMARY KEY,
                                    reference    VARCHAR(100) NOT NULL UNIQUE,
                                    type         VARCHAR(30)  NOT NULL,
                                    description  VARCHAR(255),
                                    currency     VARCHAR(3)   NOT NULL,
                                    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE ledger_entry (
                              id                   UUID        PRIMARY KEY,
                              transaction_id       UUID        NOT NULL REFERENCES ledger_transaction (id),
                              account_id           UUID        NOT NULL,
                              direction            VARCHAR(6)  NOT NULL,
                              amount_minor         BIGINT      NOT NULL,
                              currency             VARCHAR(3)  NOT NULL,
                              balance_after_minor  BIGINT      NOT NULL,
                              created_at           TIMESTAMPTZ NOT NULL,

                              CONSTRAINT chk_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
                              CONSTRAINT chk_entry_amount_positive CHECK (amount_minor > 0)
);

CREATE INDEX idx_ledger_entry_account ON ledger_entry (account_id, created_at);
CREATE INDEX idx_ledger_entry_transaction ON ledger_entry (transaction_id);

-- Append-only: the database itself refuses to change or delete history.
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger tables are append-only (% on %)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_transaction_immutable
    BEFORE UPDATE OR DELETE ON ledger_transaction
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

CREATE TRIGGER trg_ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();