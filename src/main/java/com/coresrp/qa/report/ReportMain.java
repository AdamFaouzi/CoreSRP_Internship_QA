package com.coresrp.qa.report;

import java.nio.file.Path;

/** Entry point: `mvn -Preport exec:java@aggregate-report [-Dqa.run.id=...]`. */
public final class ReportMain {

    public static void main(String[] args) {
        Path report = new ReportAggregator().generate();
        System.out.println("Report written to: " + report.toAbsolutePath());
    }
}
