ALTER TABLE transfer
    ADD COLUMN destination_bank_code          VARCHAR(10),
    ADD COLUMN destination_account_name       VARCHAR(150),
    ADD COLUMN provider_recipient_code        VARCHAR(50),
    ADD COLUMN provider_reference             VARCHAR(60),
    ADD COLUMN provider_transfer_code         VARCHAR(50),
    ADD COLUMN reversal_ledger_transaction_id UUID;

CREATE UNIQUE INDEX uq_transfer_provider_reference ON transfer (provider_reference);

-- Small, fast index for the "find stuck transfers" query we'll write in Step 10
CREATE INDEX idx_transfer_processing ON transfer (status, updated_at) WHERE status = 'PROCESSING';

ALTER TABLE transfer
    ADD CONSTRAINT chk_external_has_bank_code
        CHECK (type <> 'EXTERNAL' OR destination_bank_code IS NOT NULL);