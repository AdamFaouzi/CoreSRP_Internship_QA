package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.config.QaConfig;
import com.coresrp.qa.ui.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API-level authorization checks the UI-level tests don't cover: does the backend enforce the
 * same restrictions as the UI, and is invoice detail access itself properly scoped?
 */
public class InvoiceApiAuthorizationTest extends BaseTest {

    @Test
    void invoiceDetailFetch_worksRegardlessOfCurrentlySelectedCompany() {
        loginAndOpenDocuments();

        expect("GET /invoices/{id} isn't company-scoped in its URL path (unlike list endpoints, " +
                "which are /companies/{companyId}/invoices) — confirming it still checks the " +
                "invoice's owning company is one the caller's org has access to, not just blindly " +
                "trusting any ID. (This can't test TRUE cross-tenant IDOR without a second, " +
                "unrelated org account — only confirms the scoping model looks sound.)");
        String invoiceJson = (String) page.evaluate(
                "id => fetch(`https://invoices.coresrp.com/invoices/${id}`, {credentials: 'include'}).then(r => r.status + '|' + r.statusText)",
                QaConfig.testInvoiceId());
        actual("GET /invoices/{id} while on default company: " + invoiceJson);

        assertTrue(invoiceJson.startsWith("200"), "should succeed — this is our own org's invoice");
    }

    @Test
    void deleteDisabledOnFreePlan_backendAlsoRejectsDirectApiCall() {
        loginAndOpenDocuments();

        expect("The UI shows 'Delete disabled (free plan)' on the invoice review page — confirming " +
                "the backend independently enforces this (rejects DELETE), rather than it being a " +
                "UI-only restriction a direct API call could bypass");
        String result = (String) page.evaluate(
                "id => fetch(`https://invoices.coresrp.com/invoices/${id}`, " +
                        "{method: 'DELETE', credentials: 'include'}).then(r => r.status + '|' + r.statusText)",
                QaConfig.testInvoiceId());
        actual("DELETE /invoices/{id} response: " + result);

        int status = Integer.parseInt(result.split("\\|")[0]);
        if (status >= 200 && status < 300) {
            recordFinding("HIGH",
                    "Delete-disabled restriction is UI-only — a direct DELETE API call succeeds despite the free plan",
                    "DELETE /invoices/" + QaConfig.testInvoiceId() + " returned " + result
                            + " even though the UI explicitly shows 'Delete disabled (free plan)'.");
        }
        assertFalse(status >= 200 && status < 300,
                "a direct DELETE call should be rejected (4xx) on a plan where the UI says deletion is disabled");
    }
}
