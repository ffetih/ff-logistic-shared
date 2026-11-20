package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.logistics.transportation.ShipmentDeliveries.Outcome;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Flow 3, from transportation's side: a completed route stop delivers its shipment, as the stop's
 * tenant, once, and publishes the delivery like any other (so flow 2 can follow). Routing is not
 * here: the implementations wire the two together.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class StopDeliveryTest {

    private static final Instant SIGNED = Instant.parse("2026-11-21T09:15:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShipmentDeliveries deliveries;

    @Autowired
    private ApplicationEvents events;

    private final String tenant = newTenant();
    private String shipmentId;
    private String shipmentNumber;

    @BeforeEach
    void aShipmentOnItsWay() throws Exception {
        String created = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.roadByEnds("PO-STOP-1"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        shipmentId = JsonPath.read(created, "$.id");
        shipmentNumber = JsonPath.read(created, "$.shipmentNumber");
    }

    private StopDelivery fromStop(String tenantId, String number) {
        return new StopDelivery(tenantId, number, "Ayse Demir", SIGNED, "RTE-5003", "IST-ESN");
    }

    @Test
    void deliversTheShipmentAsTheStopsTenant() throws Exception {
        assertThat(deliveries.deliver(fromStop(tenant, shipmentNumber))).isEqualTo(Outcome.DELIVERED);

        mvc.perform(get("/api/v1/tms/shipments/" + shipmentId).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.receivedBy").value("Ayse Demir"))
                .andExpect(jsonPath("$.deliveredAt").value("2026-11-21T09:15:00Z"));
        assertThat(events.stream(ShipmentDelivered.class)).singleElement()
                .satisfies(delivered -> assertThat(delivered.shipmentNumber()).isEqualTo(shipmentNumber));
        assertThat(TenantContext.findCurrent()).isEmpty();
    }

    @Test
    void aSecondCompletionChangesNothing() {
        assertThat(deliveries.deliver(fromStop(tenant, shipmentNumber))).isEqualTo(Outcome.DELIVERED);
        assertThat(deliveries.deliver(fromStop(tenant, shipmentNumber))).isEqualTo(Outcome.ALREADY_DELIVERED);

        assertThat(events.stream(ShipmentDelivered.class)).hasSize(1);
    }

    @Test
    void anotherTenantsShipmentIsNotFound() {
        assertThat(deliveries.deliver(fromStop(newTenant(), shipmentNumber))).isEqualTo(Outcome.UNKNOWN_SHIPMENT);
        assertThat(deliveries.deliver(fromStop(tenant, "SHP-99999"))).isEqualTo(Outcome.UNKNOWN_SHIPMENT);

        assertThat(events.stream(ShipmentDelivered.class)).isEmpty();
    }
}
