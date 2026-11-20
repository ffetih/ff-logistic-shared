package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.CONTAINER;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.SSCC;
import static com.siose.ff.logistics.transportation.ShipmentApiTest.shipment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.logistics.transportation.domain.Shipment;
import com.siose.ff.logistics.transportation.domain.ShipmentRepository;
import com.siose.ff.logistics.transportation.service.ShipmentService;
import com.siose.ff.logistics.transportation.service.ShipmentService.Delivery;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class ShipmentDeliveryTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private ShipmentService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final String tenant = newTenant();

    private String id;

    /** A shipment on its way: planned, then departed. */
    @BeforeEach
    void departed() throws Exception {
        String body = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(shipment(CONTAINER, SSCC, "Cologne Eifeltor"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        id = JsonPath.read(body, "$.id");
        mvc.perform(post("/api/v1/tms/shipments/" + id + "/departure").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk());
    }

    private ResultActions deliver(String receivedBy) throws Exception {
        return mvc.perform(post("/api/v1/tms/shipments/" + id + "/delivery").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receivedBy\": \"" + receivedBy + "\"}")
                .with(as(tenant, "LOGISTICS_PLANNER")));
    }

    @Test
    void recordsWhoSignedAndWhen() throws Exception {
        deliver("M. Schneider")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.receivedBy").value("M. Schneider"))
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());
    }

    @Test
    void aSecondConfirmationIsRefused() throws Exception {
        deliver("M. Schneider").andExpect(status().isOk());

        deliver("K. Wagner")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:shipment-status-conflict"));
        mvc.perform(get("/api/v1/tms/shipments/" + id).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.receivedBy").value("M. Schneider"));
    }

    @Test
    void needsTheNameOfWhoSigned() throws Exception {
        deliver(" ").andExpect(status().isBadRequest());
    }

    /**
     * Two drivers' apps confirm at the same moment, played out step by step. Driver A's
     * transaction reads the shipment (DEPARTED). Before A writes, driver B's confirmation runs
     * in a transaction of its own and commits. A, still holding the shipment it read, then
     * delivers it too: its UPDATE carries the version it read, finds no such row, and fails.
     */
    @Test
    void aConfirmationThatReadTheShipmentBeforeTheOtherCommittedFails() throws Exception {
        TransactionTemplate driverA = new TransactionTemplate(transactionManager);
        TransactionTemplate driverB = new TransactionTemplate(transactionManager);
        driverB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        TenantContext.setCurrent(new TenantInfo(tenant, "test.user", Set.of("LOGISTICS_PLANNER"), "TEST"));
        try {
            assertThatThrownBy(() -> driverA.executeWithoutResult(transaction -> {
                Shipment readByA = shipments.findById(UUID.fromString(id)).orElseThrow();
                driverB.executeWithoutResult(other -> service.deliver(UUID.fromString(id), new Delivery("K. Wagner")));
                readByA.deliver(Instant.now(), "M. Schneider");
            })).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            TenantContext.clear();
        }

        String body = mvc.perform(get("/api/v1/tms/shipments/" + id).with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("DELIVERED");
        assertThat((String) JsonPath.read(body, "$.receivedBy")).isEqualTo("K. Wagner");
    }
}
