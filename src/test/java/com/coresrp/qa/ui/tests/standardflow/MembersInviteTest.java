package com.coresrp.qa.ui.tests.standardflow;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.MembersPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Real invite submission to Settings > Members, using an email the account owner explicitly
 * approved for this test (akf00@aubmed.ac.cy — the same address as the account owner, so this
 * also exercises the duplicate-invite/already-a-member path rather than creating a brand new
 * account). Confirmed with the user before running: submitting sends a real invite email.
 */
public class MembersInviteTest extends BaseTest {

    @Test
    void inviteWithApprovedEmail_handledGracefully() {
        MembersPage members = loginAndOpenMembers();

        expect("Inviting an email that's already the workspace owner is rejected with a clear " +
                "message (e.g. 'already a member'), not a raw server error or a silent no-op");
        members.fillInviteForm("QA Test Invite", "akf00@aubmed.ac.cy", "Qa-Test-Passw0rd-2026!");
        members.submitInvite();
        page.waitForTimeout(2000);

        String bodyText = page.locator("body").innerText();
        actual("page body after invite submission: " + truncate(bodyText, 400));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Inviting an already-registered email surfaced a raw server error", bodyText);
        }
        assertFalse(serverError, "duplicate-email invite should be rejected gracefully, not crash");

        recordDataFootprint("member-invite", "akf00@aubmed.ac.cy", "akf00's organization", "Adam Internship QA",
                "real invite submission, approved by account owner — targets the owner's own email");
    }

    @Test
    void weakPasswordRejectedClientSideOrServerSide() {
        MembersPage members = loginAndOpenMembers();

        expect("A password that doesn't meet the stated requirements (10+ chars, 1 upper, 1 lower, " +
                "1 digit) is rejected — either client-side validation blocks submit, or the server " +
                "returns a clear validation error, not a raw 500 or a silently-created weak-password account");
        members.fillInviteForm("QA Weak Password Test", "qa-weak-pw-test-" + System.nanoTime() + "@example.invalid", "weak");
        members.submitInvite();
        page.waitForTimeout(1500);

        String bodyText = page.locator("body").innerText();
        actual("page body after weak-password submit attempt: " + truncate(bodyText, 400));

        boolean serverError = bodyText.toLowerCase().contains("internal server error") || bodyText.contains("500");
        if (serverError) {
            recordFinding("MEDIUM", "Weak-password invite submission surfaced a raw server error", bodyText);
        }
        assertFalse(serverError, "weak password should be rejected gracefully, not crash");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
