package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Flow 1, from transportation's side: a road leg given by its ends gets its distance through the
 * port, and the straight-line estimate when the port does not answer. Routing itself is a stand-in
 * here; the implementations test their own wiring.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LegDistanceTest {

    /** Routing as a test controls it: an answer, or a failure. */
    static class StandInRouting implements DistanceLookup {

        volatile Double answer;
        final List<String> asked = new ArrayList<>();

        @Override
        public double roadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
            asked.add(fromLatitude + "," + fromLongitude + " -> " + toLatitude + "," + toLongitude);
            if (answer == null) {
                throw new IllegalStateException("routing is down");
            }
            return answer;
        }
    }

    @TestConfiguration
    static class Routing {

        @Bean
        StandInRouting standInRouting() {
            return new StandInRouting();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StandInRouting routing;

    private final String tenant = newTenant();

    @BeforeEach
    void routingUp() {
        routing.answer = 11.42;
        routing.asked.clear();
    }

    private ResultActions plan(String body) throws Exception {
        return mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(as(tenant, "LOGISTICS_PLANNER")));
    }

    @Test
    void aRoadLegGivenByItsEndsGetsRoutingsDistance() throws Exception {
        plan(TmsRequests.roadByEnds("PO-ENDS-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legs[0].distanceKm").value(11.42))
                .andExpect(jsonPath("$.legs[0].distanceSource").value("ROUTING"))
                .andExpect(jsonPath("$.footprint.totalDistanceKm").value(11.42));

        assertThat(routing.asked).containsExactly("41.1,28.65 -> 41.03,28.67");
    }

    @Test
    void withoutAnAnswerTheLegGetsTheEstimate() throws Exception {
        routing.answer = null;

        plan(TmsRequests.roadByEnds("PO-ENDS-2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legs[0].distanceKm").value(9.87))
                .andExpect(jsonPath("$.legs[0].distanceSource").value("ESTIMATE"));
    }

    @Test
    void aRequestRepeatedWhileRoutingIsDownIsStillTheSameRequest() throws Exception {
        String first = plan(TmsRequests.roadByEnds("PO-ENDS-3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legs[0].distanceSource").value("ROUTING"))
                .andReturn().getResponse().getContentAsString();

        routing.answer = null;

        plan(TmsRequests.roadByEnds("PO-ENDS-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentNumber").value((String) JsonPath.read(first, "$.shipmentNumber")))
                .andExpect(jsonPath("$.legs[0].distanceSource").value("ROUTING"));
    }

    @Test
    void aGivenDistanceIsNotAsked() throws Exception {
        plan(TmsRequests.shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.legs[*].distanceSource").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("GIVEN"))));

        assertThat(routing.asked).isEmpty();
    }

    @Test
    void onlyARoadLegsDistanceIsWorkedOut() throws Exception {
        plan(TmsRequests.roadByEnds("PO-ENDS-4").replace("\"mode\": \"ROAD\"", "\"mode\": \"RAIL\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Leg 1 goes by RAIL: only a road leg's distance can be worked out from its ends; give its distanceKm"));
    }

    @Test
    void aLegNeedsADistanceOrBothEnds() throws Exception {
        plan(TmsRequests.roadByEnds("PO-ENDS-5").replace("\"destinationLongitude\": 28.67,", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Leg 1 needs all four coordinates of its ends, or none"));
        plan(TmsRequests.roadByEnds("PO-ENDS-6").replaceAll("\"(origin|destination)(Latitude|Longitude)\": [0-9.]+,?", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Leg 1 needs its distanceKm, or the coordinates of its ends"));
    }
}
