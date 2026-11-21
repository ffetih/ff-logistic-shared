package com.siose.ff.contracts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What one Kafka topic carries, written once for both implementations: an example message, the
 * field that is the message's key, the fields that may be {@code null}, and the formats of the
 * fields a reader parses ({@code uuid}, {@code instant}). The contracts are the files
 * {@code contracts/<topic>.json} of this jar.
 *
 * <p>A producer's test sends a real event through a broker in its JVM, reads the message back and
 * {@link #verify verifies} it. The modulith and every service run the same check against the same
 * contract, so a message has the same fields, types and key whichever side sent it; a reader's test
 * parses the {@link #example}. A message must have exactly the contract's fields: a new field is
 * added to the contract first, so both sides add it, and readers learn of it from one place.
 */
public final class EventContract {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String topic;
    private final String key;
    private final Set<String> nullable;
    private final Map<String, String> formats;
    private final JsonNode example;

    private EventContract(String topic, JsonNode contract) {
        this.topic = topic;
        this.key = contract.required("key").asString();
        this.nullable = new LinkedHashSet<>();
        contract.path("nullable").forEach(field -> nullable.add(field.asString()));
        this.formats = new LinkedHashMap<>();
        contract.path("formats").properties().forEach(entry -> formats.put(entry.getKey(), entry.getValue().asString()));
        this.example = contract.required("example");
        if (!example.has(key)) {
            throw new IllegalStateException("The contract of " + topic + " names a key, " + key + ", its example lacks");
        }
    }

    /** Every topic the product sends to, as listed in {@code contracts/index.txt}. */
    public static List<String> topics() {
        return read("contracts/index.txt").lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    /** @throws IllegalArgumentException when the topic has no contract: a topic nobody wrote down */
    public static EventContract of(String topic) {
        if (!topics().contains(topic)) {
            throw new IllegalArgumentException("No contract for topic " + topic + ": add contracts/" + topic
                    + ".json to ff-contracts, and the topic to contracts/index.txt");
        }
        try {
            return new EventContract(topic, JSON.readTree(read("contracts/" + topic + ".json")));
        } catch (JacksonException e) {
            throw new IllegalStateException("The contract of " + topic + " is not valid JSON", e);
        }
    }

    public String topic() {
        return topic;
    }

    /** The example message, as JSON: what a reader's test parses. */
    public String example() {
        return example.toString();
    }

    /** The value of the example's key field: the key a producer would send it with. */
    public String exampleKey() {
        return example.get(key).asString();
    }

    /**
     * Everything wrong with a message as it was read from the topic, one line each; empty when it
     * keeps the contract.
     */
    public List<String> violations(String recordKey, String message) {
        List<String> wrong = new ArrayList<>();
        JsonNode actual;
        try {
            actual = JSON.readTree(message);
        } catch (JacksonException e) {
            return List.of("not JSON: " + message);
        }
        if (actual == null || !actual.isObject()) {
            return List.of("not a JSON object: " + message);
        }
        for (String field : example.propertyNames()) {
            JsonNode value = actual.get(field);
            if (value == null) {
                wrong.add(field + " is missing");
            } else if (value.isNull()) {
                if (!nullable.contains(field)) {
                    wrong.add(field + " is null, and the contract does not allow it");
                }
            } else if (value.getNodeType() != example.get(field).getNodeType()) {
                wrong.add(field + " is " + describe(value) + ", the contract has " + describe(example.get(field)));
            } else if (formats.containsKey(field) && !hasFormat(value.asString(), formats.get(field))) {
                wrong.add(field + " is not " + article(formats.get(field)) + ": " + value);
            }
        }
        for (String field : actual.propertyNames()) {
            if (!example.has(field)) {
                wrong.add(field + " is not in the contract");
            }
        }
        JsonNode keyValue = actual.get(key);
        if (keyValue != null && !keyValue.isNull() && !keyValue.asString().equals(recordKey)) {
            wrong.add("the record's key is " + recordKey + ", the contract keys it by " + key + " ("
                    + keyValue.asString() + ")");
        }
        return wrong;
    }

    /** @throws AssertionError listing everything wrong with the message */
    public void verify(String recordKey, String message) {
        List<String> wrong = violations(recordKey, message);
        if (!wrong.isEmpty()) {
            throw new AssertionError("A message on " + topic + " breaks its contract:\n  - "
                    + String.join("\n  - ", wrong) + "\nThe message: " + message);
        }
    }

    private static boolean hasFormat(String value, String format) {
        try {
            switch (format) {
                case "uuid" -> UUID.fromString(value);
                case "instant" -> Instant.parse(value);
                default -> throw new IllegalStateException("Unknown format " + format);
            }
            return true;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return false;
        }
    }

    private static String describe(JsonNode value) {
        return value.getNodeType().name().toLowerCase() + " " + value;
    }

    private static String article(String format) {
        return switch (format) {
            case "uuid" -> "a UUID";
            case "instant" -> "an ISO-8601 instant";
            default -> format;
        };
    }

    private static String read(String resource) {
        try (InputStream in = EventContract.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from ff-contracts");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
