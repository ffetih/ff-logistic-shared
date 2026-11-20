package com.siose.ff.logistics.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.siose.ff.logistics.routing.planning.Location;
import com.siose.ff.logistics.routing.planning.OsrmRoadMatrix;
import com.siose.ff.logistics.routing.planning.RoadMatrixUnavailableException;
import com.siose.ff.logistics.routing.planning.Travel;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Against a stand-in OSRM on a local port: what goes out, and what comes back from each answer. */
class OsrmRoadMatrixTest {

    private static final Location DEPOT = new Location(41.10, 28.65);
    private static final List<Location> STOPS = List.of(new Location(41.03, 28.67), new Location(40.98, 28.87));

    private HttpServer osrm;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> acceptEncoding = new AtomicReference<>();

    private String serve(int status, String json, Duration delay) throws IOException {
        osrm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        osrm.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getRawPath());
            acceptEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        osrm.start();
        return "http://127.0.0.1:" + osrm.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (osrm != null) {
            osrm.stop(0);
        }
    }

    private static OsrmRoadMatrix matrix(String baseUrl) {
        return new OsrmRoadMatrix(baseUrl, Duration.ofMillis(500), Duration.ofMillis(500));
    }

    @Test
    void readsMetresAndSecondsIntoKilometresAndMinutes() throws IOException {
        String url = serve(200, """
                {"code": "Ok",
                 "distances": [[0, 11234.4, 26120], [11190, 0, 19876.5], [26700, 20001, 0]],
                 "durations": [[0, 812.2, 1500], [790, 0, 1260], [1530, 1300.5, 0]]}""", Duration.ZERO);

        Travel travel = matrix(url).between(DEPOT, STOPS, 40);

        assertThat(travel.kilometers(0, 1)).isEqualTo(11.23);
        assertThat(travel.kilometers(1, 2)).isEqualTo(19.88);
        assertThat(travel.kilometers(2, 1)).isEqualTo(20.0);
        assertThat(travel.minutes(0, 1)).isEqualTo(14);   // 812.2 s, rounded up
        assertThat(travel.minutes(1, 2)).isEqualTo(21);   // exactly 21 minutes stay 21
    }

    @Test
    void sendsTheCoordinatesUnencodedAndAsksForNoCompression() throws IOException {
        String url = serve(200, """
                {"code": "Ok", "distances": [[0, 1, 1], [1, 0, 1], [1, 1, 0]],
                 "durations": [[0, 1, 1], [1, 0, 1], [1, 1, 0]]}""", Duration.ZERO);

        matrix(url).between(DEPOT, STOPS, 40);

        assertThat(requestPath.get()).isEqualTo("/table/v1/driving/28.65,41.1;28.67,41.03;28.87,40.98");
        assertThat(acceptEncoding.get()).isNull();
    }

    @Test
    void aPointWithoutARoadIsUnavailable() throws IOException {
        String url = serve(200, """
                {"code": "Ok", "distances": [[0, 1, null], [1, 0, 1], [1, 1, 0]],
                 "durations": [[0, 1, null], [1, 0, 1], [1, 1, 0]]}""", Duration.ZERO);

        assertThatThrownBy(() -> matrix(url).between(DEPOT, STOPS, 40))
                .isInstanceOf(RoadMatrixUnavailableException.class)
                .hasMessageContaining("no road between two points");
    }

    @Test
    void aRefusedTableIsUnavailable() throws IOException {
        String url = serve(400, "{\"code\": \"TooBig\", \"message\": \"Too many table coordinates\"}", Duration.ZERO);

        assertThatThrownBy(() -> matrix(url).between(DEPOT, STOPS, 40))
                .isInstanceOf(RoadMatrixUnavailableException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("TooBig");
    }

    @Test
    void aStalledServerTimesOut() throws IOException {
        String url = serve(200, "{\"code\": \"Ok\"}", Duration.ofSeconds(3));
        long started = System.nanoTime();

        assertThatThrownBy(() -> matrix(url).between(DEPOT, STOPS, 40))
                .isInstanceOf(RoadMatrixUnavailableException.class)
                .hasMessageContaining("did not answer");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void noServerIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> matrix("http://127.0.0.1:" + closedPort).between(DEPOT, STOPS, 40))
                .isInstanceOf(RoadMatrixUnavailableException.class);
    }
}
