-- The signed e-CMR of a shipment, a PDF in object storage: its location, s3://<bucket>/<key>,
-- reserved the first time an upload link is asked for. No endpoint and no credentials in it.
ALTER TABLE tms_shipments ADD COLUMN ecmr_document_uri VARCHAR(300);
