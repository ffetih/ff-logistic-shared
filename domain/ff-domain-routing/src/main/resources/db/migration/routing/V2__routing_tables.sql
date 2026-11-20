-- Routing: territories, and route plans with their stops in driving order. Every row belongs to one
-- tenant; the area filters by tenant_id on every query (Hibernate @TenantId). The tables keep the
-- reference's short name, geo_*. Ported from the reference's V10 and V11 in one script.

-- Route numbers: RTE-5001, RTE-5002, ...
CREATE SEQUENCE geo_route_seq START WITH 5001 INCREMENT BY 1;

CREATE TABLE geo_territories (
    id                     UUID PRIMARY KEY,
    tenant_id              VARCHAR(64)      NOT NULL,
    territory_code         VARCHAR(40)      NOT NULL,
    name                   VARCHAR(140)     NOT NULL,
    hub_gln                VARCHAR(13)      NOT NULL,
    center_latitude        DOUBLE PRECISION NOT NULL,
    center_longitude       DOUBLE PRECISION NOT NULL,
    coverage_radius_km     DOUBLE PRECISION NOT NULL,
    active_point_count     INTEGER          NOT NULL,
    weekly_visit_target    INTEGER          NOT NULL,
    workload_balance_score DOUBLE PRECISION NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_geo_territories_code UNIQUE (tenant_id, territory_code)
);

CREATE TABLE geo_route_plans (
    id                       UUID PRIMARY KEY,
    tenant_id                VARCHAR(64)      NOT NULL,
    route_number             VARCHAR(20)      NOT NULL,
    territory_code           VARCHAR(40)      NOT NULL,
    vehicle_plate            VARCHAR(20)      NOT NULL,
    depot_latitude           DOUBLE PRECISION NOT NULL,
    depot_longitude          DOUBLE PRECISION NOT NULL,
    vehicle_capacity_kg      DOUBLE PRECISION NOT NULL,
    average_speed_kmh        DOUBLE PRECISION NOT NULL,
    loaded_weight_kg         DOUBLE PRECISION NOT NULL,
    capacity_utilization_pct DOUBLE PRECISION NOT NULL,
    total_distance_km        DOUBLE PRECISION NOT NULL,
    total_minutes            INTEGER          NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_geo_route_plans_number UNIQUE (route_number)
);

CREATE INDEX idx_geo_route_plans_tenant_created ON geo_route_plans (tenant_id, created_at DESC);

-- A stop may have a time window: the earliest and the latest arrival, in minutes after the
-- vehicle leaves the depot. Both or neither.
CREATE TABLE geo_route_stops (
    id               UUID PRIMARY KEY,
    tenant_id        VARCHAR(64)      NOT NULL,
    route_plan_id    UUID             NOT NULL REFERENCES geo_route_plans (id),
    sequence_no      INTEGER          NOT NULL,
    stop_code        VARCHAR(60)      NOT NULL,
    customer_name    VARCHAR(140)     NOT NULL,
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,
    demand_weight_kg DOUBLE PRECISION NOT NULL,
    service_minutes  INTEGER          NOT NULL,
    leg_distance_km  DOUBLE PRECISION NOT NULL,
    arrival_minutes  INTEGER          NOT NULL,
    ready_minutes    INTEGER,
    due_minutes      INTEGER,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_geo_route_stops_sequence UNIQUE (route_plan_id, sequence_no)
);
