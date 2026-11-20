package com.siose.ff.logistics.warehousing;

import static com.siose.ff.logistics.TestCallers.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.logistics.standards.Gs1Standards;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Builds the WMS requests the tests send, with valid GS1 numbers. */
final class WmsRequests {

    static final String COMPANY_PREFIX = "8690001";

    private WmsRequests() {
    }

    /** A GLN of the test company: prefix, five-digit location reference, check digit. */
    static String gln(int locationReference) {
        String payload = COMPANY_PREFIX + "%05d".formatted(locationReference);
        return payload + Gs1Standards.checkDigit(payload);
    }

    /** A GTIN-13 of the test company: prefix, five-digit item reference, check digit. */
    static String gtin(int itemReference) {
        String payload = COMPANY_PREFIX + "%05d".formatted(itemReference);
        return payload + Gs1Standards.checkDigit(payload);
    }

    static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static String warehouse(String code, String gln) {
        return """
                {"code": "%s", "name": "Istanbul Hadimkoy Hub", "gln": "%s", "gs1CompanyPrefix": "%s",
                 "countryCode": "TR", "city": "Istanbul", "latitude": 41.1025, "longitude": 28.6512,
                 "palletCapacity": 12000}
                """.formatted(code, gln, COMPANY_PREFIX);
    }

    static String bin(String code, int palletCapacity) {
        return """
                {"code": "%s", "kind": "RACK", "palletCapacity": %d}
                """.formatted(code, palletCapacity);
    }

    /** Creates a warehouse for the tenant and returns its id. */
    static String createWarehouse(MockMvc mvc, String tenant, String code, int locationReference) throws Exception {
        String body = mvc.perform(json("/api/v1/wms/warehouses", warehouse(code, gln(locationReference)))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    static String createBin(MockMvc mvc, String tenant, String warehouseId, String code, int palletCapacity)
            throws Exception {
        String body = mvc.perform(json("/api/v1/wms/warehouses/" + warehouseId + "/bins", bin(code, palletCapacity))
                        .with(as(tenant, "WAREHOUSE_OPERATOR")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
