package com.siose.ff.logistics.routing.planning;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Road distances and drive times from an OSRM server's table service: one request for the whole
 * matrix, distances in metres, durations in seconds, along the real road network. The requested
 * average speed is not used; OSRM knows each road's speed.
 *
 * <p>Three things the plain defaults get wrong, each met against a real server:
 * <ul>
 * <li>Both timeouts are set. Without them a stalled OSRM holds the route request, and its
 *     thread, as long as the client waits. A timeout is reported like any other failure: 503.</li>
 * <li>The coordinates are written into the path, not passed as URI variables: Spring encodes
 *     variables strictly, so {@code ,} and {@code ;} would leave as {@code %2C} and {@code %3B},
 *     which OSRM rejects. They are numbers only; nothing in them needs encoding.</li>
 * <li>No compressed answers. Spring asks for gzip and deflate by default, and OSRM's deflate is
 *     not what Spring's inflater expects ({@code incorrect header check}). The answers are small.</li>
 * </ul>
 *
 * <p>OSRM refuses a table larger than its {@code --max-table-size} (100 points by default), so
 * that limit has to fit {@code GEO_MAX_STOPS} plus the depot.
 */
@Component
@ConditionalOnProperty(name = "ff.routing.road-matrix", havingValue = "osrm")
public class OsrmRoadMatrix implements RoadMatrix {

    private final RestClient client;

    public OsrmRoadMatrix(
            @Value("${ff.routing.osrm.base-url}") String baseUrl,
            @Value("${ff.routing.osrm.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${ff.routing.osrm.read-timeout:PT5S}") Duration readTimeout) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requests.setReadTimeout(readTimeout);
        requests.enableCompression(false);
        this.client = RestClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(requests)
                .build();
    }

    /** The parts of OSRM's answer this matrix reads. A cell is null where no road connects two points. */
    record TableAnswer(String code, List<List<Double>> distances, List<List<Double>> durations) {
    }

    @Override
    public Travel between(Location depot, List<Location> stops, double averageSpeedKmh) {
        String coordinates = Stream.concat(Stream.of(depot), stops.stream())
                .map(point -> point.longitude() + "," + point.latitude()) // OSRM: longitude first
                .collect(Collectors.joining(";"));
        TableAnswer answer;
        try {
            answer = client.get()
                    .uri("/table/v1/driving/" + coordinates + "?annotations=distance,duration")
                    .retrieve()
                    .body(TableAnswer.class);
        } catch (RestClientResponseException ex) {
            // OSRM answers 400 with a code of its own, for example TooBig above --max-table-size.
            throw new RoadMatrixUnavailableException("The road network service (OSRM) refused the table: HTTP "
                    + ex.getStatusCode().value() + " " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new RoadMatrixUnavailableException("The road network service (OSRM) did not answer", ex);
        }
        if (answer == null || !"Ok".equals(answer.code())) {
            throw new RoadMatrixUnavailableException("The road network service (OSRM) refused the table: "
                    + (answer == null ? "empty answer" : answer.code()));
        }
        int n = stops.size() + 1;
        double[][] kilometers = new double[n][n];
        int[][] minutes = new int[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                if (from == to) {
                    continue;
                }
                Double metres = cell(answer.distances(), from, to);
                Double seconds = cell(answer.durations(), from, to);
                if (metres == null || seconds == null) {
                    throw new RoadMatrixUnavailableException(
                            "The road network service (OSRM) knows no road between two points of this route");
                }
                kilometers[from][to] = Math.round(metres / 10.0) / 100.0;
                minutes[from][to] = (int) Math.ceil(seconds / 60.0);
            }
        }
        return new Travel(kilometers, minutes);
    }

    private static Double cell(List<List<Double>> matrix, int from, int to) {
        if (matrix == null || from >= matrix.size() || matrix.get(from) == null || to >= matrix.get(from).size()) {
            return null;
        }
        return matrix.get(from).get(to);
    }
}
