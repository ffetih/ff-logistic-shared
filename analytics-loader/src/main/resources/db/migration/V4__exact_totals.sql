-- Distances and CO2e as exact decimals instead of binary floating point. A double cannot hold 1565.1
-- exactly, so a sum of three came out as 4695.299999999999; a NUMERIC sum of three is 4695.3. Three
-- decimals: kilometres to the metre, kilograms to the gram. Every value a message carries fits;
-- one with more decimals is rounded to the third as it is stored.
ALTER TABLE shipment_deliveries
    ALTER COLUMN total_distance_km TYPE NUMERIC(12, 3) USING round(total_distance_km::numeric, 3),
    ALTER COLUMN saved_co2e_kg     TYPE NUMERIC(12, 3) USING round(saved_co2e_kg::numeric, 3);

-- The monthly sums: wider, a month of a tenant adds up many deliveries.
ALTER TABLE tenant_delivery_monthly
    ALTER COLUMN total_distance_km TYPE NUMERIC(15, 3) USING round(total_distance_km::numeric, 3),
    ALTER COLUMN saved_co2e_kg     TYPE NUMERIC(15, 3) USING round(saved_co2e_kg::numeric, 3);

-- The figures already counted were summed as doubles: count every month again, exactly.
INSERT INTO delivery_month_changes (tenant_id, delivery_month)
SELECT DISTINCT tenant_id, delivery_month FROM tenant_delivery_monthly
ON CONFLICT DO NOTHING;
