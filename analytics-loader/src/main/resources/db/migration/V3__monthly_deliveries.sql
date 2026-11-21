-- Deliveries per tenant and calendar month (UTC), counted by the rollup (MonthlyDeliveryRollup)
-- from shipment_deliveries. Reports read this instead of scanning every delivery.
CREATE TABLE tenant_delivery_monthly (
    tenant_id           VARCHAR(64)      NOT NULL,
    delivery_month      DATE             NOT NULL,  -- the month's first day
    delivered_shipments INTEGER          NOT NULL,
    total_distance_km   DOUBLE PRECISION NOT NULL,
    saved_co2e_kg       DOUBLE PRECISION NOT NULL,
    refreshed_at        TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (tenant_id, delivery_month)
);

-- The months whose figures are out of date: the rollup's to-do list, so it recounts only those
-- instead of every delivery ever loaded. DeliveryStore adds a row in the same statement that stores
-- a delivery, so a stored delivery always has its mark: no clock to compare, nothing to miss. A
-- delivery read twice is stored once and marks nothing. The rollup deletes the marks it recounts,
-- in the statement that recounts them.
CREATE TABLE delivery_month_changes (
    tenant_id      VARCHAR(64) NOT NULL,
    delivery_month DATE        NOT NULL,
    PRIMARY KEY (tenant_id, delivery_month)
);

-- The same row-level security as every table here (V2). Readers see the figures, not the to-do list.
ALTER TABLE tenant_delivery_monthly ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_delivery_monthly FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_delivery_monthly
    USING (tenant_id = current_setting('app.tenant_id', true)
           OR current_setting('app.all_tenants', true) = 'on')
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
GRANT SELECT ON tenant_delivery_monthly TO analytics_reader;

ALTER TABLE delivery_month_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE delivery_month_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON delivery_month_changes
    USING (tenant_id = current_setting('app.tenant_id', true)
           OR current_setting('app.all_tenants', true) = 'on')
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- Every month that already has deliveries, so the first run counts what was loaded before this
-- version. The month rule is DeliveryMonth's: the UTC calendar month of delivered_at.
INSERT INTO delivery_month_changes (tenant_id, delivery_month)
SELECT DISTINCT tenant_id, date_trunc('month', delivered_at AT TIME ZONE 'UTC')::date
FROM shipment_deliveries;
