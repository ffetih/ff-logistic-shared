package com.siose.ff.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Keycloak realm deployed by ff-logistic-runtime, found by walking up from the working directory.
 * In this layout the files live at {@code runtime/deploy/platform/keycloak}. The first path is the
 * one used when the deploy tree sits at the repository root.
 */
final class RealmFiles {

    private static final List<String> CANDIDATES = List.of(
            "runtime/deploy/platform/keycloak",
            "deploy/platform/keycloak");

    private RealmFiles() {
    }

    static Path directory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            for (String relative : CANDIDATES) {
                Path candidate = dir.resolve(relative);
                if (Files.exists(candidate.resolve("ff-logistics-realm.json"))) {
                    return candidate;
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("deploy/platform/keycloak not found above " + Path.of("").toAbsolutePath());
    }
}
