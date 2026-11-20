package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.CONTAINER;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.SSCC;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.shipment;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentLifecycleTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    private String id;

    @BeforeEach
    void planned() throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        id = JsonPath.read(body, "$.id");
    }

    private ResultActions step(String step) throws Exception {
        return step(step, tenant);
    }

    private ResultActions step(String step, String caller) throws Exception {
        return mvc.perform(post("/api/v1/tms/shipments/" + id + "/" + step).with(as(caller, "LOGISTICS_PLANNER")));
    }

    @Test
    void movesForwardStepByStep() throws Exception {
        step("pickup")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.pickedUpAt").isNotEmpty())
                .andExpect(jsonPath("$.departedAt").doesNotExist());
        step("departure")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPARTED"))
                .andExpect(jsonPath("$.pickedUpAt").isNotEmpty())
                .andExpect(jsonPath("$.departedAt").isNotEmpty());
    }

    @Test
    void mayLeaveOutAStep() throws Exception {
        step("departure")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPARTED"))
                .andExpect(jsonPath("$.pickedUpAt").doesNotExist());
    }

    @Test
    void neverRepeatsOrGoesBack() throws Exception {
        step("departure").andExpect(status().isOk());

        step("departure")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:shipment-status-conflict"));
        step("pickup")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("is DEPARTED; it cannot become PICKED_UP")));
    }

    @Test
    void anotherTenantCannotMoveIt() throws Exception {
        step("pickup", newTenant()).andExpect(status().isNotFound());
    }
}
