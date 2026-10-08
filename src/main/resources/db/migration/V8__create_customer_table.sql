CREATE TABLE customer (
                          id            UUID         PRIMARY KEY,
                          full_name     VARCHAR(150) NOT NULL,
                          email         VARCHAR(254) NOT NULL,
                          api_key_hash  VARCHAR(64)  NOT NULL,
                          status        VARCHAR(20)  NOT NULL,
                          created_at    TIMESTAMPTZ  NOT NULL,

                          CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_customer_email ON customer (lower(email));
CREATE UNIQUE INDEX uq_customer_api_key_hash ON customer (api_key_hash);