ALTER TABLE account
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE account
    ADD CONSTRAINT chk_account_type CHECK (account_type IN ('CUSTOMER', 'SYSTEM'));

ALTER TABLE account DROP CONSTRAINT chk_account_balance_non_negative;

ALTER TABLE account
    ADD CONSTRAINT chk_account_balance_non_negative
    CHECK (account_type = 'SYSTEM' OR balance_minor >= 0);