-- inbox_events: idempotency for Kafka consumers (mail-service)
-- event_id + consumer_name UNIQUE ensures at-most-once processing per event per consumer
-- trace_id: observability, Jaeger correlation

CREATE TABLE IF NOT EXISTS inbox_events (
    event_id       VARCHAR(64)  NOT NULL,
    consumer_name  VARCHAR(100) NOT NULL,
    trace_id       VARCHAR(64),
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_inbox_events_consumer ON inbox_events (consumer_name);
