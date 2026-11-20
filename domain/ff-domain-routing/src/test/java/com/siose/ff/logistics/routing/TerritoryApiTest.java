package com.siose.ff.logistics.routing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.routing.GeoRequests.HUB_GLN;
import static com.siose.ff.logistics.routing.GeoRequests.json;
import static com.siose.ff.logistics.routing.GeoRequests.territory;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TerritoryApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    @Test
    void createsATerritoryWithItsWorkloadScore() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", territory("ist-eu", HUB_GLN)).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.territoryCode").value("IST-EU"))
                // 70 + density min(20, 420 / 35 = 12) + frequency min(10, 840 / 420 x 5 = 10)
                .andExpect(jsonPath("$.workloadBalanceScore").value(92.0));

        mvc.perform(get("/api/v1/geo/territories").with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/geo/territories").with(as(newTenant(), "DISPATCHER")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void refusesAHubGlnWithAWrongCheckDigit() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", territory("IST-EU", "8690123450016")).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:domain-validation"));
    }

    @Test
    void refusesASecondTerritoryWithTheSameCode() throws Exception {
        mvc.perform(json("/api/v1/geo/territories", territory("IST-EU", HUB_GLN)).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isCreated());
        mvc.perform(json("/api/v1/geo/territories", territory("ist-eu", HUB_GLN)).with(as(tenant, "DISPATCHER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:territory-code-conflict"));
        // Codes are per tenant.
        mvc.perform(json("/api/v1/geo/territories", territory("IST-EU", HUB_GLN)).with(as(newTenant(), "DISPATCHER")))
                .andExpect(status().isCreated());
    }

    @Test
    void isForDispatchersAndAdmins() throws Exception {
        mvc.perform(get("/api/v1/geo/territories").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isForbidden());
    }
}
