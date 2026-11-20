package com.siose.ff.logistics.warehousing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.warehousing.WmsRequests.createWarehouse;
import static com.siose.ff.logistics.warehousing.WmsRequests.gln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.logistics.warehousing.InboundArrivals.Outcome;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Flow 2 on warehousing's side, the same for both implementations: a delivered shipment opens an
 * inbound arrival at the warehouse with that GLN, once, in the shipment's tenant, without a request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InboundArrivalsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InboundArrivals arrivals;

    private final String tenant = newTenant();

    private String warehouseGln;

    @BeforeEach
    void aWarehouse() throws Exception {
        createWarehouse(mvc, tenant, "IST-DOCK-01", 31);
        warehouseGln = gln(31);
    }

    private ShipmentArrival arrival(String tenantId, UUID shipment, String gln) {
        return new ShipmentArrival(tenantId, shipment, "SHP-10042", gln, "106141411234567897", 22000,
                Instant.parse("2026-11-20T10:00:00Z"));
    }

    @Test
    void aDeliveryOpensAnArrivalAtTheWarehouse() throws Exception {
        UUID shipment = UUID.randomUUID();

        assertThat(arrivals.open(arrival(tenant, shipment, warehouseGln))).isEqualTo(Outcome.OPENED);

        mvc.perform(get("/api/v1/wms/arrivals").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shipmentId").value(shipment.toString()))
                .andExpect(jsonPath("$[0].shipmentNumber").value("SHP-10042"))
                .andExpect(jsonPath("$[0].sscc").value("106141411234567897"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void theSameShipmentTwiceOpensOneArrival() throws Exception {
        UUID shipment = UUID.randomUUID();

        assertThat(arrivals.open(arrival(tenant, shipment, warehouseGln))).isEqualTo(Outcome.OPENED);
        assertThat(arrivals.open(arrival(tenant, shipment, warehouseGln))).isEqualTo(Outcome.ALREADY_OPEN);

        mvc.perform(get("/api/v1/wms/arrivals").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aDestinationThatIsNotOneOfTheTenantsWarehousesOpensNothing() throws Exception {
        assertThat(arrivals.open(arrival(tenant, UUID.randomUUID(), gln(99)))).isEqualTo(Outcome.NOT_A_WAREHOUSE);
        // The same GLN in another tenant: that tenant has no such warehouse.
        assertThat(arrivals.open(arrival(newTenant(), UUID.randomUUID(), warehouseGln)))
                .isEqualTo(Outcome.NOT_A_WAREHOUSE);

        mvc.perform(get("/api/v1/wms/arrivals").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void runsAsTheArrivalsTenantAndPutsTheCallersBack() {
        assertThat(TenantContext.findCurrent()).isEmpty();
        arrivals.open(arrival(tenant, UUID.randomUUID(), warehouseGln));
        assertThat(TenantContext.findCurrent()).isEmpty();

        TenantContext.setCurrent(new TenantInfo(newTenant(), "someone", Set.of(), "TEST"));
        try {
            String before = TenantContext.requireTenantId();
            assertThat(arrivals.open(arrival(tenant, UUID.randomUUID(), warehouseGln))).isEqualTo(Outcome.OPENED);
            assertThat(TenantContext.requireTenantId()).isEqualTo(before);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void onlyTheWarehousesStaffSeeTheDocks() throws Exception {
        mvc.perform(get("/api/v1/wms/arrivals").with(as(tenant, "DISPATCHER")))
                .andExpect(status().isForbidden());
    }
}
