package io.trustlens.authorization;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PolicyEvaluatorTest {
    @Test
    void disabledAccountIsAlwaysDenied() {
        var result = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            false, true, true, true, true, true));
        assertFalse(result.allowed());
        assertEquals("ACCOUNT_DISABLED", result.reasonCode());
    }

    @Test
    void inactiveDocumentIsDeniedEvenToOwner() {
        var result = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            true, false, true, false, false, false));
        assertFalse(result.allowed());
        assertEquals("DOCUMENT_INACTIVE", result.reasonCode());
    }

    @Test
    void activeGrantAllowsRequest() {
        var result = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            true, true, false, false, true, false));
        assertTrue(result.allowed());
        assertEquals("ACTIVE_GRANT", result.reasonCode());
    }

    @Test
    void missingOrRevokedGrantDefaultsToDeny() {
        var result = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            true, true, false, false, false, false));
        assertFalse(result.allowed());
        assertEquals("NO_MATCHING_POLICY", result.reasonCode());
    }

    @Test
    void publicReadDoesNotAutomaticallyPermitDownload() {
        var read = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            true, true, false, true, false, false));
        var download = PolicyEvaluator.evaluate(new PolicyEvaluator.Facts(
            true, true, false, false, false, false));
        assertTrue(read.allowed());
        assertFalse(download.allowed());
    }
}
