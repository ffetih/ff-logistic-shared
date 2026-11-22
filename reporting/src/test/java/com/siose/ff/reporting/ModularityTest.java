package com.siose.ff.reporting;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** generation publishes the event; delivery may see that event and nothing stored behind it. */
class ModularityTest {

    @Test
    void generationAndDeliveryStayApart() {
        ApplicationModules.of(ReportingApplication.class).verify();
    }
}
