package com.siose.ff.logistics.warehousing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.warehousing.WmsRequests.createWarehouse;
import static com.siose.ff.logistics.warehousing.WmsRequests.gtin;
import static com.siose.ff.logistics.warehousing.WmsRequests.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AsnApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    private String warehouseId;

    @BeforeEach
    void warehouse() throws Exception {
        warehouseId = createWarehouse(mvc, tenant, "IST-HUB-01", 10);
    }

    private String asn(String number, String warehouse, String... gtins) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < gtins.length; i++) {
            lines.append(i == 0 ? "" : ", ").append("""
                    {"gtin": "%s", "skuCode": "brake-pad-%d", "quantity": %d}""".formatted(gtins[i], i + 1, 10 * (i + 1)));
        }
        return """
                {"asnNumber": "%s", "warehouseId": "%s", "supplierName": "Bosch Bursa",
                 "expectedArrival": "2026-11-24", "lines": [%s]}
                """.formatted(number, warehouse, lines);
    }

    @Test
    void createsAnAsnWithNumberedLines() throws Exception {
        String body = mvc.perform(json("/api/v1/wms/asns", asn("desadv-4711", warehouseId, gtin(1), gtin(2)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asnNumber").value("DESADV-4711"))
                .andExpect(jsonPath("$.status").value("EXPECTED"))
                .andExpect(jsonPath("$.expectedArrival").value("2026-11-24"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].lineNumber").value(1))
                .andExpect(jsonPath("$.lines[0].skuCode").value("BRAKE-PAD-1"))
                .andExpect(jsonPath("$.lines[1].expectedQuantity").value(20))
                .andExpect(jsonPath("$.lines[1].receivedQuantity").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/wms/asns/" + id).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[1].gtin").value(gtin(2)));
        mvc.perform(get("/api/v1/wms/asns").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void refusesABadGtinOrTheSameGtinTwice() throws Exception {
        mvc.perform(json("/api/v1/wms/asns", asn("ASN-1", warehouseId, "4006381333932"))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("4006381333932")));
        mvc.perform(json("/api/v1/wms/asns", asn("ASN-2", warehouseId, gtin(3), gtin(3)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("twice")));
    }

    @Test
    void refusesAnAsnWithoutLines() throws Exception {
        mvc.perform(json("/api/v1/wms/asns", asn("ASN-3", warehouseId))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:invalid-payload"));
    }

    @Test
    void anAsnNumberIsUsedOncePerTenant() throws Exception {
        mvc.perform(json("/api/v1/wms/asns", asn("ASN-4", warehouseId, gtin(4))).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated());
        mvc.perform(json("/api/v1/wms/asns", asn("asn-4", warehouseId, gtin(5))).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:asn-number-taken"));
    }

    @Test
    void anotherTenantsWarehouseCannotReceiveAnAsn() throws Exception {
        mvc.perform(json("/api/v1/wms/asns", asn("ASN-5", warehouseId, gtin(6))).with(as(newTenant(), "WAREHOUSE_OPERATOR")))
                .andExpect(status().isNotFound());
    }
}
