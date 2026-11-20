-- WMS: warehouses, the bin locations inside them, and the ASNs (advance shipping notices) that
-- announce what a supplier is about to deliver. Every row belongs to one tenant; the app filters
-- by tenant_id on every query (Hibernate @TenantId), so the codes are unique per tenant only.

CREATE TABLE wms_warehouses (
    id                 UUID PRIMARY KEY,
    tenant_id          VARCHAR(64)      NOT NULL,
    code               VARCHAR(40)      NOT NULL,
    name               VARCHAR(140)     NOT NULL,
    gln                VARCHAR(13)      NOT NULL,
    gs1_company_prefix VARCHAR(10)      NOT NULL,
    country_code       VARCHAR(2)       NOT NULL,
    city               VARCHAR(80)      NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    pallet_capacity    INTEGER          NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wms_warehouses_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_wms_warehouses_gln UNIQUE (tenant_id, gln)
);

CREATE TABLE wms_bin_locations (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    warehouse_id    UUID        NOT NULL REFERENCES wms_warehouses (id),
    code            VARCHAR(40) NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    pallet_capacity INTEGER     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wms_bin_locations_code UNIQUE (warehouse_id, code)
);

-- version: optimistic locking. Two requests that change the same ASN at once cannot both win.
CREATE TABLE wms_asns (
    id               UUID PRIMARY KEY,
    tenant_id        VARCHAR(64)  NOT NULL,
    asn_number       VARCHAR(35)  NOT NULL,
    warehouse_id     UUID         NOT NULL REFERENCES wms_warehouses (id),
    supplier_name    VARCHAR(140) NOT NULL,
    expected_arrival DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    version          BIGINT       NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wms_asns_number UNIQUE (tenant_id, asn_number)
);

CREATE TABLE wms_asn_lines (
    id                UUID PRIMARY KEY,
    tenant_id         VARCHAR(64) NOT NULL,
    asn_id            UUID        NOT NULL REFERENCES wms_asns (id),
    line_number       INTEGER     NOT NULL,
    gtin              VARCHAR(14) NOT NULL,
    sku_code          VARCHAR(60) NOT NULL,
    expected_quantity INTEGER     NOT NULL,
    received_quantity INTEGER     NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wms_asn_lines_number UNIQUE (asn_id, line_number)
);

CREATE INDEX idx_wms_warehouses_tenant ON wms_warehouses (tenant_id, created_at DESC);
CREATE INDEX idx_wms_asns_tenant ON wms_asns (tenant_id, expected_arrival);
CREATE INDEX idx_wms_asn_lines_asn ON wms_asn_lines (asn_id);
