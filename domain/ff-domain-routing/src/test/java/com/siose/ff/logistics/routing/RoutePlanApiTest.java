package com.siose.ff.logistics.routing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.routing.GeoRequests.HUB_GLN;
import static com.siose.ff.logistics.routing.GeoRequests.fourStops;
import static com.siose.ff.logistics.routing.GeoRequests.json;
import static com.siose.ff.logistics.routing.GeoRequests.route;
import static com.siose.ff.logistics.routing.GeoRequests.stop;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class RoutePlanApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    @BeforeEach
    void createTerritory() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", GeoRequests.territory("IST-EU", HUB_GLN))
                        .with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated());
    }

    private ResultActions plan(String body) throws Exception {
        return mvc.perform(json("/api/v1/geo/routes", body).with(as(tenant, "DISPATCHER")));
    }

    @Test
    void ordersTheStopsAndTimesThem() throws Exception {
        String body = plan(route("ist-eu", fourStops()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routeNumber").value(Matchers.startsWith("RTE-")))
                .andExpect(jsonPath("$.territoryCode").value("IST-EU"))
                .andExpect(jsonPath("$.vehiclePlate").value("34 FF 0417"))
                .andExpect(jsonPath("$.stops[*].stopCode").value(
                        Matchers.contains("IST-ESN", "IST-BKY", "IST-BSK", "IST-KDK")))
                .andExpect(jsonPath("$.stops[*].sequenceNo").value(Matchers.contains(1, 2, 3, 4)))
                // Straight line x 1.24 per leg; at 40 km/h, rounded up; 15 minutes at every stop.
                .andExpect(jsonPath("$.stops[*].legDistanceKm").value(Matchers.contains(9.87, 21.92, 16.69, 7.69)))
                .andExpect(jsonPath("$.stops[*].arrivalMinutes").value(Matchers.contains(15, 63, 104, 131)))
                .andExpect(jsonPath("$.totalDistanceKm").value(56.17))
                .andExpect(jsonPath("$.totalMinutes").value(146))
                .andExpect(jsonPath("$.loadedWeightKg").value(1400.0))
                .andExpect(jsonPath("$.capacityUtilizationPct").value(40.0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/geo/routes/" + id).with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].stopCode").value("IST-ESN"));
        mvc.perform(get("/api/v1/geo/routes").with(as(tenant, "DISPATCHER")))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/geo/routes/" + id).with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void meetsTheTimeWindows() throws Exception {
        // Kadikoy takes deliveries until minute 70. Straight there arrives at 64; Esenyurt first
        // would arrive at 15 + 15 + 57 = 87.
        plan(route("IST-EU", stop("IST-ESN", "Esenyurt Depot Shop", 41.03, 28.67, 200) + ","
                + stop("IST-KDK", "Kadikoy Market", 40.99, 29.03, 400, 0, 70)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stops[*].stopCode").value(Matchers.contains("IST-KDK", "IST-ESN")))
                .andExpect(jsonPath("$.stops[0].readyMinutes").value(0))
                .andExpect(jsonPath("$.stops[0].dueMinutes").value(70))
                .andExpect(jsonPath("$.stops[0].arrivalMinutes").value(64))
                .andExpect(jsonPath("$.stops[1].arrivalMinutes").value(64 + 15 + 57))
                .andExpect(jsonPath("$.stops[1].readyMinutes").doesNotExist());
    }

    @Test
    void refusesWindowsNoOrderCanMeet() throws Exception {
        plan(route("IST-EU", stop("IST-KDK", "Kadikoy Market", 40.99, 29.03, 400, 0, 20)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("No order of the stops keeps every stop inside its time window"));
    }

    @Test
    void refusesHalfAWindow() throws Exception {
        plan(route("IST-EU", stop("IST-KDK", "Kadikoy Market", 40.99, 29.03, 400, 30, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Stop IST-KDK needs both readyMinutes and dueMinutes, or neither"));
        plan(route("IST-EU", stop("IST-KDK", "Kadikoy Market", 40.99, 29.03, 400, 60, 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The time window of stop IST-KDK ends before it starts"));
    }


    @Test
    void refusesAStopCodeUsedTwice() throws Exception {
        plan(route("IST-EU", stop("STOP_1", "A", 41.0, 28.7, 10) + "," + stop("stop 1", "B", 41.01, 28.71, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Stop codes STOP_1 and stop 1 are the same code in this route"));
    }

    @Test
    void refusesMoreLoadThanTheVehicleCarries() throws Exception {
        plan(route("IST-EU", stop("A", "A", 41.0, 28.7, 2000) + "," + stop("B", "B", 41.01, 28.71, 1501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("3501.0 kg")));
    }

    @Test
    void refusesMoreThan99StopsBeforePlanning() throws Exception {
        String stops = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> stop("S" + i, "Customer " + i, 41.0 + i / 1000.0, 28.7, 1))
                .collect(Collectors.joining(","));
        plan(route("IST-EU", stops))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Matchers.startsWith("A route takes at most 99 stops; this one has 100")));
    }

    @Test
    void refusesAnUnknownTerritory() throws Exception {
        plan(route("IST-AS", fourStops()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Territory IST-AS does not exist"));
        // Another tenant's territory is unknown too.
        mvc.perform(json("/api/v1/geo/routes", route("IST-EU", fourStops())).with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAStopOffTheMap() throws Exception {
        plan(route("IST-EU", stop("A", "A", 91.0, 28.7, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:invalid-payload"));
    }

    @Test
    void noDocumentLinksWithoutAStore() throws Exception {
        String body = mvc.perform(json("/api/v1/geo/routes", route("IST-EU", fourStops())).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/geo/routes/" + com.jayway.jsonpath.JsonPath.read(body, "$.id") + "/stops/IST-ESN/epod/upload-link")
                        .with(as(tenant, "DISPATCHER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("object-store-unavailable")));
    }
}
