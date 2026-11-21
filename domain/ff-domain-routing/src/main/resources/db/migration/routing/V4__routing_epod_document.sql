-- A stop's proof of delivery, the consignee's signature as a PNG in object storage: its location,
-- s3://<bucket>/<key>, reserved the first time an upload link is asked for.
ALTER TABLE geo_route_stops ADD COLUMN epod_document_uri VARCHAR(300);
