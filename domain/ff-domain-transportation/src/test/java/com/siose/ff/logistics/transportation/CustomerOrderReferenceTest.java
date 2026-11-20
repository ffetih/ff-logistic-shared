package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.CONTAINER;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.SSCC;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.shipment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerOrderReferenceTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    static String order(String reference) {
        return TmsRequests.order(reference);
    }

    private ResultActions plan(String body, String caller) throws Exception {
        return mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(as(caller, "LOGISTICS_PLANNER")));
    }

    @Test
    void aRepeatedRequestGetsTheSameShipment() throws Exception {
        String first = plan(order("PO-4711"), tenant)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerOrderReference").value("PO-4711"))
                .andReturn().getResponse().getContentAsString();
        String again = plan(order("PO-4711"), tenant)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(again, "$.shipmentNumber")).isEqualTo(JsonPath.read(first, "$.shipmentNumber"));
        mvc.perform(get("/api/v1/tms/shipments").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void theSameNumberWithAnotherPlanIsAnotherOrder() throws Exception {
        plan(order("PO-4712"), tenant).andExpect(status().isCreated());

        plan(order("PO-4712").replace("\"grossWeightKg\": 22000", "\"grossWeightKg\": 18000"), tenant)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:customer-order-conflict"));
    }

    @Test
    void everyTenantHasItsOwnOrderNumbers() throws Exception {
        plan(order("PO-4713"), tenant).andExpect(status().isCreated());
        plan(order("PO-4713"), newTenant()).andExpect(status().isCreated());
    }

    @Test
    void withoutANumberEveryRequestIsANewShipment() throws Exception {
        String body = shipment(CONTAINER, SSCC, "Cologne Eifeltor");
        plan(body, tenant).andExpect(status().isCreated());
        plan(body, tenant).andExpect(status().isCreated());
    }

    /**
     * Eight retries of the same order at once. However they interleave, one creates the
     * shipment and the others get it: they found it, or they lost the insert to the unique
     * index and read the winner.
     */
    @Test
    void parallelRetriesCreateOneShipment() throws Exception {
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> answers = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<MockHttpServletResponse> retry = () -> {
                    start.await();
                    return plan(order("PO-4714"), tenant).andReturn().getResponse();
                };
                answers.add(pool.submit(retry));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<String> numbers = new ArrayList<>();
            for (Future<MockHttpServletResponse> answer : answers) {
                MockHttpServletResponse response = answer.get();
                statuses.add(response.getStatus());
                numbers.add(JsonPath.read(response.getContentAsString(), "$.shipmentNumber"));
            }
            assertThat(statuses).containsOnly(201, 200).filteredOn(code -> code == 201).hasSize(1);
            assertThat(numbers).containsOnly(numbers.getFirst());
        } finally {
            pool.shutdownNow();
        }
        mvc.perform(get("/api/v1/tms/shipments").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
