CREATE TABLE transfer (
                          id                          UUID         PRIMARY KEY,
                          idempotency_key             VARCHAR(80)  NOT NULL UNIQUE,
                          type                        VARCHAR(20)  NOT NULL,
                          status                      VARCHAR(20)  NOT NULL,
                          source_account_id           UUID         NOT NULL,
                          source_account_number       VARCHAR(10)  NOT NULL,
                          destination_account_id      UUID,
                          destination_account_number  VARCHAR(10)  NOT NULL,
                          amount_minor                BIGINT       NOT NULL,
                          currency                    VARCHAR(3)   NOT NULL,
                          narration                   VARCHAR(100),
                          ledger_transaction_id       UUID,
                          failure_reason              VARCHAR(255),
                          version                     BIGINT       NOT NULL DEFAULT 0,
                          created_at                  TIMESTAMPTZ  NOT NULL,
                          updated_at                  TIMESTAMPTZ  NOT NULL,

                          CONSTRAINT chk_transfer_amount_positive CHECK (amount_minor > 0),
                          CONSTRAINT chk_transfer_type CHECK (type IN ('INTERNAL', 'EXTERNAL')),
                          CONSTRAINT chk_transfer_status
                              CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
                          CONSTRAINT chk_transfer_currency CHECK (currency IN ('NGN', 'USD'))
);

CREATE INDEX idx_transfer_source ON transfer (source_account_id, created_at);
CREATE INDEX idx_transfer_destination ON transfer (destination_account_id, created_at);