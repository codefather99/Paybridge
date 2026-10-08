CREATE TABLE webhook_event (
                               id            UUID           PRIMARY KEY,
                               provider      VARCHAR(20)    NOT NULL,
                               event_type    VARCHAR(60),
                               reference     VARCHAR(80),
                               payload       VARCHAR(10000) NOT NULL,
                               result        VARCHAR(30)    NOT NULL,
                               received_at   TIMESTAMPTZ    NOT NULL,
                               processed_at  TIMESTAMPTZ
);

CREATE INDEX idx_webhook_event_reference ON webhook_event (reference);