package com.siose.ff.logistics.routing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.routing.GeoRequests.HUB_GLN;
import static com.siose.ff.logistics.routing.GeoRequests.json;
import static com.siose.ff.logistics.routing.GeoRequests.route;
import static com.siose.ff.logistics.routing.GeoRequests.stop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Flow 3, from routing's side: a stop is completed once, and its completion is published with the
 * shipment it delivers. Transportation's side is tested in its own area and in the implementations.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class StopCompletionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents events;

    private final String tenant = newTenant();
    private String routeId;

    @BeforeEach
    void aRouteWithAShipmentOnOneStop() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", GeoRequests.territory("IST-EU", HUB_GLN))
                .with(as(tenant, "TENANT_ADMIN"))).andExpect(status().isCreated());
        String stops = String.join(",",
                stop("IST-ESN", "Esenyurt Depot Shop", 41.03, 28.67, 200)
                        .replace("}", ", \"shipmentNumber\": \"SHP-10042\"}"),
                stop("IST-BKY", "Bakirkoy Bakery", 40.98, 28.87, 500));
        String created = mvc.perform(json("/api/v1/geo/routes", route("IST-EU", stops)).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stops[0].shipmentNumber").value("SHP-10042"))
                .andExpect(jsonPath("$.stops[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.stops[1].shipmentNumber").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        routeId = JsonPath.read(created, "$.id");
    }

    private ResultActions complete(String stopCode) throws Exception {
        return mvc.perform(json("/api/v1/geo/routes/" + routeId + "/stops/" + stopCode + "/completion",
                "{\"receivedBy\": \"Ayse Demir\"}").with(as(tenant, "DISPATCHER")));
    }

    @Test
    void completesAStopAndPublishesItWithItsShipment() throws Exception {
        complete("IST-ESN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stops[0].receivedBy").value("Ayse Demir"))
                .andExpect(jsonPath("$.stops[0].completedAt").isNotEmpty())
                .andExpect(jsonPath("$.stops[1].status").value("PLANNED"));

        assertThat(events.stream(RouteStopCompleted.class)).singleElement().satisfies(completed -> {
            assertThat(completed.tenantId()).isEqualTo(tenant);
            assertThat(completed.routeNumber()).startsWith("RTE-");
            assertThat(completed.stopCode()).isEqualTo("IST-ESN");
            assertThat(completed.shipmentNumber()).isEqualTo("SHP-10042");
            assertThat(completed.receivedBy()).isEqualTo("Ayse Demir");
        });
    }

    @Test
    void aStopWithoutAShipmentIsPublishedToo() throws Exception {
        complete("ist-bky").andExpect(status().isOk());

        assertThat(events.stream(RouteStopCompleted.class)).singleElement()
                .satisfies(completed -> assertThat(completed.shipmentNumber()).isNull());
    }

    @Test
    void aStopIsCompletedOnce() throws Exception {
        complete("IST-ESN").andExpect(status().isOk());

        complete("IST-ESN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("stop-status-conflict")));
        assertThat(events.stream(RouteStopCompleted.class)).hasSize(1);
    }

    @Test
    void anUnknownStopIsRefused() throws Exception {
        complete("IST-XYZ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.endsWith("has no stop IST-XYZ")));
        assertThat(events.stream(RouteStopCompleted.class)).isEmpty();
    }
}
