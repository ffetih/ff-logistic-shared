package com.siose.ff.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Enough of the Keycloak protocol and admin API for the identity service tests.
 */
final class FakeKeycloak {

    static final String JWKS = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test\",\"use\":\"sig\"}]}";

    private static final HttpServer SERVER = start();

    static volatile String lastUserBody;
    static volatile String lastRoleBody;
    static volatile String lastClientBody;
    static volatile String lastRoleRemovalBody;

    /** The integration client the fake knows, and its secret. */
    static final String INTEGRATION_CLIENT = "integration-ff-eu-freight.northfield";
    static final String INTEGRATION_SECRET = "northfield-secret";

    private FakeKeycloak() {
    }

    static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    static void stop() {
        SERVER.stop(0);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", FakeKeycloak::handle);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if ("GET".equals(method) && path.endsWith("/protocol/openid-connect/certs")) {
                send(exchange, 200, JWKS);
                return;
            }
            if ("POST".equals(method) && path.endsWith("/protocol/openid-connect/logout")) {
                send(exchange, 204, "");
                return;
            }
            if ("POST".equals(method) && "/realms/master/protocol/openid-connect/token".equals(path)) {
                send(exchange, 200, token("admin-token", null));
                return;
            }
            if ("POST".equals(method) && "/realms/ff-logistics/protocol/openid-connect/token".equals(path)) {
                handleToken(exchange, body);
                return;
            }
            if ("POST".equals(method) && "/admin/realms/ff-logistics/users".equals(path)) {
                lastUserBody = body;
                exchange.getResponseHeaders().set("Location", baseUrl() + "/admin/realms/ff-logistics/users/user-42");
                send(exchange, 201, "");
                return;
            }
            if ("POST".equals(method) && "/admin/realms/ff-logistics/clients".equals(path)) {
                lastClientBody = body;
                if (body.contains("\"integration-ff-eu-freight.taken\"")) {
                    send(exchange, 409, "{\"errorMessage\":\"Client already exists\"}");
                    return;
                }
                exchange.getResponseHeaders().set("Location", baseUrl() + "/admin/realms/ff-logistics/clients/client-7");
                send(exchange, 201, "");
                return;
            }
            if ("GET".equals(method) && "/admin/realms/ff-logistics/clients/client-7/service-account-user".equals(path)) {
                send(exchange, 200, "{\"id\":\"sa-7\",\"username\":\"service-account-client-7\"}");
                return;
            }
            if ("GET".equals(method) && "/admin/realms/ff-logistics/clients/client-7/client-secret".equals(path)) {
                send(exchange, 200, "{\"type\":\"secret\",\"value\":\"generated-secret\"}");
                return;
            }
            if ("GET".equals(method) && path.startsWith("/admin/realms/ff-logistics/roles/")) {
                String role = path.substring(path.lastIndexOf('/') + 1);
                if ("WAREHOUSE_OPERATOR".equals(role) || "LOGISTICS_PLANNER".equals(role)
                        || "default-roles-ff-logistics".equals(role)) {
                    send(exchange, 200, "{\"id\":\"role-" + role + "\",\"name\":\"" + role + "\"}");
                } else {
                    send(exchange, 404, "{}");
                }
                return;
            }
            if ("DELETE".equals(method) && "/admin/realms/ff-logistics/users/sa-7/role-mappings/realm".equals(path)) {
                lastRoleRemovalBody = body;
                send(exchange, 204, "");
                return;
            }
            if ("POST".equals(method) && path.matches("/admin/realms/ff-logistics/users/(user-42|sa-7)/role-mappings/realm")) {
                lastRoleBody = body;
                send(exchange, 204, "");
                return;
            }
            send(exchange, 500, "{\"error\":\"unexpected\"}");
        } finally {
            exchange.close();
        }
    }

    private static void handleToken(HttpExchange exchange, String body) throws IOException {
        Map<String, String> form = parseForm(body);
        switch (form.getOrDefault("grant_type", "")) {
            case "password" -> {
                if ("nexus.planner".equals(form.get("username")) && "fflogistics2026".equals(form.get("password"))) {
                    send(exchange, 200, token("access-1", "refresh-1"));
                } else {
                    send(exchange, 401, "{\"error\":\"invalid_grant\"}");
                }
            }
            case "refresh_token" -> {
                if ("refresh-1".equals(form.get("refresh_token"))) {
                    send(exchange, 200, token("access-2", "refresh-2"));
                } else {
                    send(exchange, 400, "{\"error\":\"invalid_grant\"}");
                }
            }
            case "client_credentials" -> {
                if ("ff-logistics-service".equals(form.get("client_id"))
                        && "ff-logistics-service-secret".equals(form.get("client_secret"))) {
                    send(exchange, 200, token("service-access", null));
                } else if (INTEGRATION_CLIENT.equals(form.get("client_id"))
                        && INTEGRATION_SECRET.equals(form.get("client_secret"))) {
                    send(exchange, 200, token("integration-access", null));
                } else {
                    send(exchange, 401, "{\"error\":\"unauthorized_client\"}");
                }
            }
            default -> send(exchange, 400, "{\"error\":\"unsupported_grant\"}");
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        if (body.isBlank()) {
            return form;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            form.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return form;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String token(String access, String refresh) {
        String refreshJson = refresh == null ? "" : ",\"refresh_token\":\"" + refresh + "\"";
        return "{\"access_token\":\"" + access + "\",\"expires_in\":300,\"token_type\":\"Bearer\"" + refreshJson + "}";
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
