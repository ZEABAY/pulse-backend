-- =============================================================
-- mail-service schema
-- =============================================================

-- -----------------------------------------------------------
-- sent_mail_log
-- Audit log for every mail delivery attempt.
-- Uses TSID (assigned by application) as primary key.
-- -----------------------------------------------------------
CREATE TABLE sent_mail_log (
    id            BIGINT       PRIMARY KEY,                          -- TSID (assigned by application)
    event_id      VARCHAR(64)  NOT NULL,                             -- source outbox event ID
    trace_id      VARCHAR(64),                                       -- W3C traceId for distributed tracing
    recipient     VARCHAR(255) NOT NULL,
    mail_type     VARCHAR(50)  NOT NULL
                      CHECK (mail_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    subject       VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL
                      CHECK (status IN ('SENT', 'FAILED')),
    error_message TEXT,
    sent_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sent_mail_log_recipient ON sent_mail_log (recipient, mail_type, sent_at);
CREATE INDEX idx_sent_mail_log_event     ON sent_mail_log (event_id);
