-- The analytics database (ff_logistics_analytics) belongs to the analytics loader: it is the
-- only writer. Tables have no module prefix; the whole database is analytics.

-- One row per delivered shipment, from the topic ff.logistics.tms.shipment-delivered. The
-- shipment id is the key: a message read twice is stored once.
CREATE TABLE shipment_deliveries (
    shipment_id              UUID PRIMARY KEY,
    tenant_id                VARCHAR(64)      NOT NULL,
    shipment_number          VARCHAR(40)      NOT NULL,
    customer_order_reference VARCHAR(64),
    total_distance_km        DOUBLE PRECISION NOT NULL,
    saved_co2e_kg            DOUBLE PRECISION NOT NULL,
    delivered_at             TIMESTAMPTZ      NOT NULL,
    loaded_at                TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_shipment_deliveries_tenant_delivered ON shipment_deliveries (tenant_id, delivered_at);
