package com.siose.ff.logistics.warehousing;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static com.siose.ff.logistics.warehousing.WmsRequests.COMPANY_PREFIX;
import static com.siose.ff.logistics.warehousing.WmsRequests.createBin;
import static com.siose.ff.logistics.warehousing.WmsRequests.createWarehouse;
import static com.siose.ff.logistics.warehousing.WmsRequests.gtin;
import static com.siose.ff.logistics.warehousing.WmsRequests.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.logistics.standards.Gs1Standards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class PalletizingApiTest {

    @Autowired
    private MockMvc mvc;

    private final String tenant = newTenant();

    private String warehouseId;
    private String asnId;

    /** A warehouse with two bins, and an ASN: 10 units of GTIN 1, 4 units of GTIN 2. */
    @BeforeEach
    void receivingSetup() throws Exception {
        warehouseId = createWarehouse(mvc, tenant, "IST-HUB-01", 10);
        createBin(mvc, tenant, warehouseId, "A01-R01-L01", 3);
        createBin(mvc, tenant, warehouseId, "A01-R01-L02", 1);
        String body = mvc.perform(json("/api/v1/wms/asns", """
                        {"asnNumber": "DESADV-1", "warehouseId": "%s", "supplierName": "Bosch Bursa",
                         "expectedArrival": "2026-11-24", "lines": [
                           {"gtin": "%s", "skuCode": "BRAKE-PAD", "quantity": 10},
                           {"gtin": "%s", "skuCode": "BRAKE-DISC", "quantity": 4}]}
                        """.formatted(warehouseId, gtin(1), gtin(2)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        asnId = JsonPath.read(body, "$.id");
    }

    private ResultActions palletize(String gtin, int quantity, String bin) throws Exception {
        return mvc.perform(json("/api/v1/wms/asns/" + asnId + "/pallets", """
                        {"gtin": "%s", "quantity": %d, "grossWeightKg": 412.5, "binCode": "%s"}
                        """.formatted(gtin, quantity, bin))
                .with(as(tenant, "WAREHOUSE_OPERATOR")));
    }

    @Test
    void aPalletGetsAValidSsccWithTheWarehousesPrefix() throws Exception {
        String body = palletize(gtin(1), 6, "a01-r01-l01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asnNumber").value("DESADV-1"))
                .andExpect(jsonPath("$.asnStatus").value("RECEIVING"))
                .andExpect(jsonPath("$.skuCode").value("BRAKE-PAD"))
                .andExpect(jsonPath("$.binCode").value("A01-R01-L01"))
                .andReturn().getResponse().getContentAsString();
        String sscc = JsonPath.read(body, "$.sscc");

        // Extension digit 0, the warehouse's company prefix, a serial, the check digit.
        assertThat(sscc).hasSize(18).startsWith("0" + COMPANY_PREFIX);
        assertThat(Gs1Standards.isValidSscc18(sscc)).isTrue();

        mvc.perform(get("/api/v1/wms/pallets/" + sscc).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6));
        mvc.perform(get("/api/v1/wms/asns/" + asnId).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(jsonPath("$.lines[0].receivedQuantity").value(6))
                .andExpect(jsonPath("$.lines[1].receivedQuantity").value(0));
    }

    @Test
    void everyPalletGetsItsOwnSscc() throws Exception {
        String first = JsonPath.read(palletize(gtin(1), 5, "A01-R01-L01")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.sscc");
        String second = JsonPath.read(palletize(gtin(1), 5, "A01-R01-L01")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.sscc");

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/api/v1/wms/asns/" + asnId + "/pallets").with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void theLastOpenLineCompletesTheAsn() throws Exception {
        palletize(gtin(1), 10, "A01-R01-L01").andExpect(jsonPath("$.asnStatus").value("RECEIVING"));
        palletize(gtin(2), 4, "A01-R01-L01").andExpect(jsonPath("$.asnStatus").value("RECEIVED"));

        palletize(gtin(2), 1, "A01-R01-L01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:asn-already-received"));
    }

    @Test
    void refusesMoreThanTheLineStillExpects() throws Exception {
        palletize(gtin(2), 3, "A01-R01-L01").andExpect(status().isCreated());

        palletize(gtin(2), 2, "A01-R01-L01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:over-receipt"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expects 1 more")));
    }

    @Test
    void refusesAGtinThatIsNotOnTheAsn() throws Exception {
        palletize(gtin(3), 1, "A01-R01-L01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:domain-validation"));
    }

    @Test
    void aFullBinTakesNoMorePallets() throws Exception {
        palletize(gtin(1), 1, "A01-R01-L02").andExpect(status().isCreated());

        palletize(gtin(1), 1, "A01-R01-L02")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:siose-ff:problem:bin-full"));
        // A refused pallet booked nothing.
        mvc.perform(get("/api/v1/wms/asns/" + asnId).with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(jsonPath("$.lines[0].receivedQuantity").value(1));
    }

    @Test
    void theBinMustBeInTheAsnsWarehouse() throws Exception {
        String otherWarehouse = createWarehouse(mvc, tenant, "IZM-HUB-01", 20);
        createBin(mvc, tenant, otherWarehouse, "Z09", 5);

        palletize(gtin(1), 1, "Z09")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no bin location Z09")));
    }

    @Test
    void anotherTenantCannotReceiveOrScan() throws Exception {
        String sscc = JsonPath.read(palletize(gtin(1), 1, "A01-R01-L01")
                .andReturn().getResponse().getContentAsString(), "$.sscc");
        String other = newTenant();

        mvc.perform(json("/api/v1/wms/asns/" + asnId + "/pallets", """
                        {"gtin": "%s", "quantity": 1, "grossWeightKg": 10, "binCode": "A01-R01-L01"}
                        """.formatted(gtin(1)))
                        .with(as(other, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/wms/pallets/" + sscc).with(as(other, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isNotFound());
    }
}
