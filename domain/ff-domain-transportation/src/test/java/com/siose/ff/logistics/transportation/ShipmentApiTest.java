package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentApiTest {

    /** The GS1 example SSCC, and the ISO 6346 example container. */
    static final String SSCC = "106141411234567897";
    static final String CONTAINER = "CSQU3054383";

    static String shipment(String equipment, String sscc, String lastLegDestination) {
        return TmsRequests.shipment(equipment, sscc, lastLegDestination);
    }

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    private ResultActions plan(String body) throws Exception {
        return mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(as(tenant, "LOGISTICS_PLANNER")));
    }

    @Test
    void plansAShipmentWithItsFootprint() throws Exception {
        String body = plan(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.shipmentNumber").value(Matchers.startsWith("SHP-")))
                .andExpect(jsonPath("$.ecmrNumber").value(Matchers.startsWith("ECMR-")))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.incoterm").value("DAP"))
                .andExpect(jsonPath("$.legs.length()").value(3))
                .andExpect(jsonPath("$.legs[1].sequenceNo").value(2))
                .andExpect(jsonPath("$.legs[1].mode").value("RAIL"))
                // The same chain as the standards module's test: 1606.22 kg against 3519.12 by road.
                .andExpect(jsonPath("$.footprint.totalDistanceKm").value(2580.0))
                .andExpect(jsonPath("$.footprint.co2eKg").value(1606.22))
                .andExpect(jsonPath("$.footprint.roadOnlyCo2eKg").value(3519.12))
                .andExpect(jsonPath("$.footprint.reductionPercent").value(54.36))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/tms/shipments/" + id).with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentCode").value(CONTAINER));
        mvc.perform(get("/api/v1/tms/shipments").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void everyShipmentGetsItsOwnNumber() throws Exception {
        String first = JsonPath.read(plan(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                .andReturn().getResponse().getContentAsString(), "$.shipmentNumber");
        String second = JsonPath.read(plan(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                .andReturn().getResponse().getContentAsString(), "$.shipmentNumber");

        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
    }

    @Test
    void refusesEquipmentWithAWrongCheckDigit() throws Exception {
        plan(shipment("CSQU3054389", SSCC, "Cologne Eifeltor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:domain-validation"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("ISO 6346")));
    }

    @Test
    void refusesAnInvalidSscc() throws Exception {
        plan(shipment(CONTAINER, "106141411234567890", "Cologne Eifeltor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("SSCC")));
    }

    @Test
    void theLegsMustReachTheDestination() throws Exception {
        plan(shipment(CONTAINER, SSCC, "Duisburg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("ends at Duisburg")));
    }

    @Test
    void aLegMustStartWhereThePreviousEnded() throws Exception {
        String gap = shipment(CONTAINER, SSCC, "Cologne Eifeltor")
                .replace("\"origin\": \"Trieste\"", "\"origin\": \"Koper\"");
        plan(gap)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Leg 2 starts at Koper")));
    }

    @Test
    void refusesAnUnknownIncoterm() throws Exception {
        plan(shipment(CONTAINER, SSCC, "Cologne Eifeltor").replace("\"DAP\"", "\"XYZ\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherTenantSeesNothingAndPlannersOnly() throws Exception {
        String id = JsonPath.read(plan(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/tms/shipments/" + id).with(as(newTenant(), "LOGISTICS_PLANNER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tms/shipments").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isForbidden());
    }
}
