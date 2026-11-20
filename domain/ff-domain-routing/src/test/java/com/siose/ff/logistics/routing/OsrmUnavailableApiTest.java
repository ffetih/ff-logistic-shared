package com.siose.ff.logistics.routing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.routing.GeoRequests.HUB_GLN;
import static com.siose.ff.logistics.routing.GeoRequests.fourStops;
import static com.siose.ff.logistics.routing.GeoRequests.json;
import static com.siose.ff.logistics.routing.GeoRequests.route;
import static com.siose.ff.logistics.routing.GeoRequests.territory;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** With OSRM configured and not running, planning answers 503 and stores nothing. */
@SpringBootTest(properties = {"ff.routing.road-matrix=osrm", "ff.routing.osrm.base-url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class OsrmUnavailableApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    @Test
    void answers503() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", territory("IST-EU", HUB_GLN)).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated());

        mvc.perform(json("/api/v1/geo/routes", route("IST-EU", fourStops())).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:road-matrix-unavailable"));
        mvc.perform(get("/api/v1/geo/routes").with(as(tenant, "DISPATCHER")))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
