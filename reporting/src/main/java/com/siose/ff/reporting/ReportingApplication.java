package com.siose.ff.reporting;

import com.siose.ff.base.app.FfApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Monthly reports as one process. {@code generation} reads the analytics rollup and writes the PDF;
 * {@code delivery} sends it. Neither module is part of the modulith or the services side.
 */
@SpringBootApplication
@EnableConfigurationProperties(AnalyticsSource.class)
public class ReportingApplication {

    public static void main(String[] args) {
        FfApplication.run(ReportingApplication.class, args);
    }
}
