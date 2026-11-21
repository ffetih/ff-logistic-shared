package com.siose.ff.logistics.transportation;

import static com.siose.ff.logistics.TestCallers.as;
import static com.siose.ff.logistics.TestCallers.newTenant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.siose.ff.base.storage.ObjectStoragePort;
import com.siose.ff.base.storage.ReferenceOnlyStorageAdapter;
import com.siose.ff.base.storage.StorageObjectDescriptor;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A shipment's e-CMR: an upload link, write-once, then a download link. Against an in-memory
 * store that signs pretend links and holds what the test says was uploaded; the real store, and
 * its conditional writes, are tested in ff-base-storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsignmentNoteLinksTest {

    /** Signs links to http://store.test; a document counts as stored once {@link #upload} names its key. */
    static class InMemoryStore extends ReferenceOnlyStorageAdapter {

        private final Set<String> stored = ConcurrentHashMap.newKeySet();

        InMemoryStore() {
            super("ff-documents");
        }

        void upload(String putUrl) {
            stored.add(putUrl.substring("http://store.test/put/".length()));
        }

        @Override
        public boolean exists(StorageObjectDescriptor document) {
            return stored.contains(document.objectKey());
        }

        @Override
        public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/put/" + document.objectKey());
        }

        @Override
        public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
            return URI.create("http://store.test/get/" + document.objectKey());
        }
    }

    @TestConfiguration
    static class Store {

        @Bean
        ObjectStoragePort objectStoragePort() {
            return new InMemoryStore();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectStoragePort storage;

    private final String tenant = newTenant();

    @Test
    void aShipmentsEcmrIsUploadedOnceAndReadBack() throws Exception {
        String shipment = mvc.perform(post("/api/v1/tms/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(TmsRequests.shipment("CSQU3054383", "106141411234567897", "Cologne Eifeltor"))
                        .with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String number = JsonPath.read(shipment, "$.shipmentNumber");
        String ecmr = "/api/v1/tms/shipments/" + JsonPath.read(shipment, "$.id") + "/ecmr";

        mvc.perform(get(ecmr + "/download-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isNotFound());
        String link = mvc.perform(post(ecmr + "/upload-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.url").value("http://store.test/put/" + tenant + "/ecmr/"
                        + number.toLowerCase() + "/ecmr.pdf"))
                .andExpect(jsonPath("$.validForSeconds").value(900))
                .andExpect(jsonPath("$.headers.Content-Type").value("application/pdf"))
                .andExpect(jsonPath("$.headers.If-None-Match").value("*"))
                .andReturn().getResponse().getContentAsString();
        // Asked again before the upload: the same location.
        mvc.perform(post(ecmr + "/upload-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(jsonPath("$.url").value(JsonPath.<String>read(link, "$.url")));

        ((InMemoryStore) storage).upload(JsonPath.read(link, "$.url"));

        mvc.perform(post(ecmr + "/upload-link").with(as(tenant, "LOGISTICS_PLANNER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(Matchers.endsWith("document-already-stored")));
        mvc.perform(get(ecmr + "/download-link").with(as(tenant, "TENANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.url").value(Matchers.endsWith("/ecmr.pdf")));
        mvc.perform(get(ecmr + "/download-link").with(as(newTenant(), "LOGISTICS_PLANNER")))
                .andExpect(status().isNotFound());
    }
}
