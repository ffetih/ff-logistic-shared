package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A shipment to a warehouse: its destination's GS1 location number is checked when it is planned,
 * and travels with the delivery event, together with what the warehouse needs to open an inbound
 * arrival (flow 2). The area only publishes the event; who hears it is the implementation's wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ShipmentToWarehouseTest {

    /** The GLN of a warehouse: company prefix 8690001, location 00001, check digit 1. */
    static final String WAREHOUSE_GLN = "8690001000011";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents events;

    private final String tenant = newTenant();

    @Test
    void theDeliveryEventCarriesTheWarehouseAndThePallet() throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.toWarehouse(WAREHOUSE_GLN)).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.destinationGln").value(WAREHOUSE_GLN))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\": \"Gate 3\"}").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());

        List<ShipmentDelivered> delivered = events.stream(ShipmentDelivered.class)
                .filter(event -> event.shipmentId().equals(UUID.fromString(id))).toList();
        assertThat(delivered).singleElement().satisfies(event -> {
            assertThat(event.tenantId()).isEqualTo(tenant);
            assertThat(event.destinationGln()).isEqualTo(WAREHOUSE_GLN);
            assertThat(event.sscc()).isEqualTo("106141411234567897");
            assertThat(event.grossWeightKg()).isEqualTo(22000);
            assertThat(event.deliveredAt()).isNotNull();
        });
    }

    @Test
    void aDestinationGlnMustBeAValidGln() throws Exception {
        mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.toWarehouse("8690001000012")).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Destination GLN")));
    }

    @Test
    void aShipmentWithoutOneHasNoWarehouseToTell() throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/delivery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedBy\": \"Gate 3\"}").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());

        assertThat(events.stream(ShipmentDelivered.class)
                .filter(event -> event.shipmentId().equals(UUID.fromString(id))))
                .singleElement().satisfies(event -> assertThat(event.destinationGln()).isNull());
    }
}
