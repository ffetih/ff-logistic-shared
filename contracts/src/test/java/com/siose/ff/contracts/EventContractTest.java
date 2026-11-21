package com.siose.ff.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The check itself: what it lets through and what it catches. */
class EventContractTest {

    private static final String TOPIC = "ff.logistics.tms.shipment-delivered";

    private final EventContract delivered = EventContract.of(TOPIC);

    static java.util.List<String> topics() {
        return EventContract.topics();
    }

    @ParameterizedTest
    @MethodSource("topics")
    void everyExampleKeepsItsOwnContract(String topic) {
        EventContract contract = EventContract.of(topic);

        assertThat(contract.violations(contract.exampleKey(), contract.example())).isEmpty();
    }

    @Test
    void listsTheThreeTopics() {
        assertThat(EventContract.topics()).containsExactly("ff.logistics.tms.shipment-delivered",
                "ff.logistics.tms.shipment-progressed", "ff.logistics.geo.route-stop-completed");
    }

    @Test
    void aNullableFieldMayBeNull() {
        String message = delivered.example().replace("\"PO-4711\"", "null");

        assertThat(delivered.violations(delivered.exampleKey(), message)).isEmpty();
    }

    @Test
    void catchesWhatAReaderWouldTripOver() {
        String key = delivered.exampleKey();
        String good = delivered.example();

        assertThat(delivered.violations(key, good.replace("\"tenantId\":\"ff-eu-freight\",", "")))
                .containsExactly("tenantId is missing");
        assertThat(delivered.violations(key, good.replace("\"shipmentNumber\":\"SHP-10011\"", "\"shipmentNumber\":null")))
                .containsExactly("shipmentNumber is null, and the contract does not allow it");
        assertThat(delivered.violations(key, good.replace("2575.0", "\"2575\"")))
                .containsExactly("totalDistanceKm is string \"2575\", the contract has number 2575.0");
        assertThat(delivered.violations(key, good.replace("\"2026-11-20T23:41:26.761285Z\"", "1795174886")))
                .containsExactly("deliveredAt is number 1795174886, the contract has string \"2026-11-20T23:41:26.761285Z\"");
        assertThat(delivered.violations(key, good.replace("2026-11-20T23:41:26.761285Z", "20.11.2026 23:41")))
                .containsExactly("deliveredAt is not an ISO-8601 instant: \"20.11.2026 23:41\"");
        assertThat(delivered.violations(key, good.replace("}", ",\"side\":\"services\"}")))
                .containsExactly("side is not in the contract");
        assertThat(delivered.violations("SHP-10011", good))
                .containsExactly("the record's key is SHP-10011, the contract keys it by shipmentId (" + key + ")");
        assertThat(delivered.violations(key, "[]")).containsExactly("not a JSON object: []");
    }

    @Test
    void verifySaysEverythingThatIsWrongAtOnce() {
        String message = delivered.example().replace("\"tenantId\":\"ff-eu-freight\",", "")
                .replace("}", ",\"side\":\"services\"}");

        assertThatThrownBy(() -> delivered.verify(delivered.exampleKey(), message))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("breaks its contract")
                .hasMessageContaining("tenantId is missing")
                .hasMessageContaining("side is not in the contract");
    }

    @Test
    void aTopicWithoutAContractIsRefused() {
        assertThatThrownBy(() -> EventContract.of("ff.logistics.tms.shipment-lost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No contract for topic ff.logistics.tms.shipment-lost");
    }
}
