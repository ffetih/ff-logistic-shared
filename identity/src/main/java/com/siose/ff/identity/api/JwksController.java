package com.siose.ff.identity.api;

import com.siose.ff.identity.keycloak.JwksCache;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class JwksController {

    private final JwksCache cache;
    private final JsonMapper jsonMapper;

    public JwksController(JwksCache cache, JsonMapper jsonMapper) {
        this.cache = cache;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> jwks() {
        return cache.document()
                .map(document -> ResponseEntity.ok(jsonMapper.readTree(document)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }
}
