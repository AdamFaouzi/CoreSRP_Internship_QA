package com.coresrp.qa.report.model;

/**
 * Records a piece of test data created in the live environment (invoice, document, upload,
 * company/org) so it can be found and cleaned up after the run.
 */
public record DataFootprintEntry(
        String type,       // invoice | document | upload | company | org
        String reference,  // ID, invoice number, or filename as shown in the app
        String org,
        String company,
        String testName,
        String note,
        String timestamp
) {
}
