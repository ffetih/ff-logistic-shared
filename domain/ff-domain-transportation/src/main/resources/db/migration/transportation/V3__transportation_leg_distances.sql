-- Flow 1: a road leg may be given by the coordinates of its two ends instead of a distance; the
-- distance then comes from routing, or from the straight-line estimate when routing does not
-- answer. The leg records which. The legs planned before were all given a distance.
ALTER TABLE tms_transport_legs ADD COLUMN distance_source VARCHAR(10) DEFAULT 'GIVEN' NOT NULL;
ALTER TABLE tms_transport_legs ADD COLUMN origin_latitude DOUBLE PRECISION;
ALTER TABLE tms_transport_legs ADD COLUMN origin_longitude DOUBLE PRECISION;
ALTER TABLE tms_transport_legs ADD COLUMN destination_latitude DOUBLE PRECISION;
ALTER TABLE tms_transport_legs ADD COLUMN destination_longitude DOUBLE PRECISION;
