-- Receiving: goods from an ASN are built into pallets, each with an SSCC label, and put away
-- into a bin location.

-- The serial reference inside every SSCC. A sequence never hands out a number twice, also
-- across restarts and several pods, which is what an SSCC needs: GS1 forbids reusing one for a
-- year after the pallet has gone. One sequence for all tenants keeps that simple.
CREATE SEQUENCE wms_sscc_serial_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE wms_pallets (
    id               UUID PRIMARY KEY,
    tenant_id        VARCHAR(64)      NOT NULL,
    sscc             VARCHAR(18)      NOT NULL,
    asn_id           UUID             NOT NULL REFERENCES wms_asns (id),
    warehouse_id     UUID             NOT NULL REFERENCES wms_warehouses (id),
    bin_location_id  UUID             NOT NULL REFERENCES wms_bin_locations (id),
    gtin             VARCHAR(14)      NOT NULL,
    sku_code         VARCHAR(60)      NOT NULL,
    quantity         INTEGER          NOT NULL,
    gross_weight_kg  DOUBLE PRECISION NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Unique across tenants: an SSCC identifies one pallet in the whole supply chain.
    CONSTRAINT uq_wms_pallets_sscc UNIQUE (sscc)
);

CREATE INDEX idx_wms_pallets_asn ON wms_pallets (asn_id);
CREATE INDEX idx_wms_pallets_bin ON wms_pallets (bin_location_id);
