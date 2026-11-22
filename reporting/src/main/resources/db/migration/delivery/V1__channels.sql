-- One preference row per tenant, and one sent row per report and channel. A second copy of the
-- same event finds the sent row and does not send again.
CREATE TABLE channel_preference (
    tenant_id        VARCHAR(64)  NOT NULL,
    email_enabled    BOOLEAN      NOT NULL,
    email_address    VARCHAR(320),
    webhook_enabled  BOOLEAN      NOT NULL,
    webhook_url      VARCHAR(500),
    PRIMARY KEY (tenant_id)
);

CREATE TABLE sent_delivery (
    report_id     UUID         NOT NULL,
    channel       VARCHAR(16)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    delivered_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (report_id, channel)
);
