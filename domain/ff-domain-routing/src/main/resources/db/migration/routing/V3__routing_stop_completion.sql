-- Flow 3: a stop may deliver one of transportation's shipments, named by its number. A stop is
-- completed once, when someone has signed for the goods. The stops planned before deliver none
-- and are still open.
ALTER TABLE geo_route_stops ADD COLUMN shipment_number VARCHAR(20);
ALTER TABLE geo_route_stops ADD COLUMN status VARCHAR(10) DEFAULT 'PLANNED' NOT NULL;
ALTER TABLE geo_route_stops ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE geo_route_stops ADD COLUMN received_by VARCHAR(140);
