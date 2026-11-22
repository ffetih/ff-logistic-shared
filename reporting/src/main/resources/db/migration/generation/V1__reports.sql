-- One row per tenant and calendar month. The PDF itself is in object storage; this row is the
-- claim-check key and the figures that were rendered, so a later read does not recount them.
CREATE TABLE report (
    id                    UUID             NOT NULL,
    tenant_id             VARCHAR(64)      NOT NULL,
    delivery_month        DATE             NOT NULL,
    object_key            VARCHAR(512)     NOT NULL,
    delivered_shipments   INTEGER          NOT NULL,
    total_distance_km     DOUBLE PRECISION NOT NULL,
    saved_co2e_kg         DOUBLE PRECISION NOT NULL,
    generated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, delivery_month)
);
