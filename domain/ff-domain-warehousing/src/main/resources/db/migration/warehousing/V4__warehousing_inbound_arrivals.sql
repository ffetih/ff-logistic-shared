-- Inbound arrivals: a shipment delivered to a warehouse is at its dock, waiting to be received.
-- Opened by the delivery itself (flow 2): in the modulith by an in-process event, on the services
-- side by a Kafka message. Either may arrive more than once; the unique key on the shipment makes
-- the second one change nothing.
CREATE TABLE wms_inbound_arrivals (
    id              UUID                     PRIMARY KEY,
    tenant_id       VARCHAR(64)              NOT NULL,
    warehouse_id    UUID                     NOT NULL REFERENCES wms_warehouses (id),
    shipment_id     UUID                     NOT NULL,
    shipment_number VARCHAR(20)              NOT NULL,
    sscc            VARCHAR(18)              NOT NULL,
    gross_weight_kg DOUBLE PRECISION         NOT NULL,
    arrived_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wms_inbound_arrivals_shipment UNIQUE (shipment_id)
);

CREATE INDEX idx_wms_inbound_arrivals_tenant ON wms_inbound_arrivals (tenant_id, arrived_at DESC);
