package com.siose.ff.logistics.warehousing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.warehousing.WmsRequests.bin;
import static com.siose.ff.logistics.warehousing.WmsRequests.createBin;
import static com.siose.ff.logistics.warehousing.WmsRequests.createWarehouse;
import static com.siose.ff.logistics.warehousing.WmsRequests.gln;
import static com.siose.ff.logistics.warehousing.WmsRequests.json;
import static com.siose.ff.logistics.warehousing.WmsRequests.warehouse;
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
class WarehouseApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    @Test
    void createsAWarehouseForTheCallersTenant() throws Exception {
        mvc.perform(json("/api/v1/wms/warehouses", warehouse("ist-hub-01", gln(10)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.code").value("IST-HUB-01"))
                .andExpect(jsonPath("$.gln").value(gln(10)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mvc.perform(get("/api/v1/wms/warehouses").with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void refusesAGlnWithAWrongCheckDigit() throws Exception {
        String gln = gln(11);
        String wrong = gln.substring(0, 12) + (char) ('0' + (gln.charAt(12) - '0' + 1) % 10);

        mvc.perform(json("/api/v1/wms/warehouses", warehouse("IST-HUB-02", wrong))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:domain-validation"));
    }

    @Test
    void refusesAGlnFromAnotherCompanyPrefix() throws Exception {
        // A valid GLN, but of GS1's example company 0614141, not of the prefix in the request.
        mvc.perform(json("/api/v1/wms/warehouses", warehouse("IST-HUB-03", "0614141000012"))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("company prefix")));
    }

    @Test
    void refusesAnIncompleteRequest() throws Exception {
        mvc.perform(json("/api/v1/wms/warehouses", "{\"code\": \"X\"}").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:invalid-payload"));
    }

    @Test
    void aCodeOrGlnIsUsedOncePerTenant() throws Exception {
        createWarehouse(mvc, tenant, "IST-HUB-04", 14);

        mvc.perform(json("/api/v1/wms/warehouses", warehouse("IST-HUB-04", gln(15)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:warehouse-code-taken"));
        mvc.perform(json("/api/v1/wms/warehouses", warehouse("IST-HUB-05", gln(14)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:warehouse-gln-taken"));

        // Another tenant may use the same code and GLN: it is a different company's warehouse list.
        createWarehouse(mvc, newTenant(), "IST-HUB-04", 14);
    }

    @Test
    void anotherTenantSeesNothing() throws Exception {
        String id = createWarehouse(mvc, tenant, "IST-HUB-06", 16);
        String other = newTenant();

        mvc.perform(get("/api/v1/wms/warehouses").with(as(other, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/wms/warehouses/" + id).with(as(other, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/wms/warehouses/" + id).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("IST-HUB-06"));
    }

    @Test
    void binLocationsBelongToTheirWarehouse() throws Exception {
        String id = createWarehouse(mvc, tenant, "IST-HUB-07", 17);
        createBin(mvc, tenant, id, "b01-r01-l02", 2);
        createBin(mvc, tenant, id, "A01-R01-L01", 1);

        mvc.perform(get("/api/v1/wms/warehouses/" + id + "/bins").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("A01-R01-L01"))
                .andExpect(jsonPath("$[1].code").value("B01-R01-L02"))
                .andExpect(jsonPath("$[1].kind").value("RACK"));

        mvc.perform(json("/api/v1/wms/warehouses/" + id + "/bins", bin("A01-R01-L01", 1))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:bin-code-taken"));
        mvc.perform(json("/api/v1/wms/warehouses/" + id + "/bins", bin("C01", 1))
                        .with(as(newTenant(), "WAREHOUSE_OPERATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void needsAWarehouseRole() throws Exception {
        mvc.perform(get("/api/v1/wms/warehouses"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wms/warehouses").with(as(tenant, "REPORT_VIEWER", "LOGISTICS_PLANNER")))
                .andExpect(status().isForbidden());
    }
}
