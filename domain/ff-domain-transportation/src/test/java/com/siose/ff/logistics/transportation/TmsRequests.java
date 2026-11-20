package com.siose.ff.logistics.transportation;

/** Builds the shipment requests the tests send; public, because other modules' tests book shipments too. */
public final class TmsRequests {

    private TmsRequests() {
    }

    /** 22 t from Istanbul: Ro-Ro to Trieste, block train to Cologne, the last 80 km by truck. */
    public static String shipment(String equipment, String sscc, String lastLegDestination) {
        return """
                {"consignorName": "Bosch Bursa", "consigneeName": "Rhein Parts GmbH",
                 "equipmentCode": "%s", "sscc": "%s", "incoterm": "DAP",
                 "origin": "Istanbul Pendik", "destination": "Cologne Eifeltor", "grossWeightKg": 22000,
                 "legs": [
                   {"mode": "SEA_RORO", "origin": "Istanbul Pendik", "destination": "Trieste",
                    "distanceKm": 1450, "carrierReference": "DFDS-PT-2611"},
                   {"mode": "RAIL", "origin": "Trieste", "destination": "Cologne Eifeltor Terminal",
                    "distanceKm": 1050, "carrierReference": "TXL-41877"},
                   {"mode": "ROAD", "origin": "Cologne Eifeltor Terminal", "destination": "%s",
                    "distanceKm": 80, "carrierReference": "TR-34-FF-0417"}]}
                """.formatted(equipment, sscc, lastLegDestination);
    }

    /** The same shipment, to a warehouse known by its GS1 location number (flow 2). */
    public static String toWarehouse(String destinationGln) {
        return shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor")
                .replaceFirst("\\{", "{\"destinationGln\": \"" + destinationGln + "\", ");
    }

    /** The same shipment, booked under a customer order number. */
    public static String order(String reference) {
        return shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor")
                .replaceFirst("\\{", "{\"customerOrderReference\": \"" + reference + "\", ");
    }
}
