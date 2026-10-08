INSERT INTO account
(id, account_number, customer_id, currency, balance_minor, status, account_type, version, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-0000000000a1', '0000000001',
     '00000000-0000-0000-0000-000000000000', 'NGN', 0, 'ACTIVE', 'SYSTEM', 0, now(), now()),
    ('00000000-0000-0000-0000-0000000000a2', '0000000002',
     '00000000-0000-0000-0000-000000000000', 'NGN', 0, 'ACTIVE', 'SYSTEM', 0, now(), now());