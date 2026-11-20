package com.siose.ff.logistics.routing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Flow 1, routing's side: a road distance for whoever plans legs. */
@SpringBootTest
@AutoConfigureMockMvc
class DistanceApiTest {

    /** The Hadimkoy hub to the Esenyurt shop: the first leg of the four-stop route, 9.87 km. */
    private static final String HADIMKOY_TO_ESENYURT =
            "/api/v1/geo/distance?fromLatitude=41.10&fromLongitude=28.65&toLatitude=41.03&toLongitude=28.67";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RoadDistances distances;

    @Test
    void answersTheRoadDistance() throws Exception {
        mvc.perform(get(HADIMKOY_TO_ESENYURT).with(as(newTenant(), "LOGISTICS_PLANNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(9.87));
    }

    @Test
    void theSameAnswerInProcess() {
        org.assertj.core.api.Assertions.assertThat(distances.roadKm(41.10, 28.65, 41.03, 28.67)).isEqualTo(9.87);
    }

    @Test
    void isForPlannersDispatchersAndAdmins() throws Exception {
        mvc.perform(get(HADIMKOY_TO_ESENYURT).with(as(newTenant(), "REPORT_VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(HADIMKOY_TO_ESENYURT)).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAPointOffTheMap() throws Exception {
        mvc.perform(get(HADIMKOY_TO_ESENYURT.replace("toLatitude=41.03", "toLatitude=91"))
                        .with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/geo/distance?fromLatitude=41.1").with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isBadRequest());
    }
}
