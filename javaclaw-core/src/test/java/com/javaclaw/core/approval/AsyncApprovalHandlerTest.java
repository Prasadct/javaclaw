package com.javaclaw.core.approval;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncApprovalHandlerTest {

    private static final ApprovalRequest SAMPLE_REQUEST = new ApprovalRequest(
            "task-123", "Test goal", "run_command", "ls -la",
            RiskLevel.HIGH, "Requires human approval"
    );

    @Test
    void submitApproval_completesHandle() throws Exception {
        var handler = new AsyncApprovalHandler(30);

        // Run handle() on a background thread
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> handler.handle(SAMPLE_REQUEST));

        // Wait for the request to appear in pending
        assertPendingWithin(handler, "task-123", 2000);

        // Submit approval
        boolean submitted = handler.submitDecision("task-123",
                ApprovalResult.approved("Looks good"));
        assertThat(submitted).isTrue();

        // Verify handle() returned the approved result
        ApprovalResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.approved()).isTrue();
        assertThat(result.reason()).isEqualTo("Looks good");
    }

    @Test
    void submitRejection_completesHandle() throws Exception {
        var handler = new AsyncApprovalHandler(30);

        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> handler.handle(SAMPLE_REQUEST));

        assertPendingWithin(handler, "task-123", 2000);

        handler.submitDecision("task-123",
                ApprovalResult.rejected("Too risky"));

        ApprovalResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo("Too risky");
    }

    @Test
    void timeout_autoRejects() throws Exception {
        var handler = new AsyncApprovalHandler(1);

        // handle() will block for 1 second then auto-reject
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> handler.handle(SAMPLE_REQUEST));

        ApprovalResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).contains("Timed out");
    }

    @Test
    void submitDecision_unknownRequestId_returnsFalse() {
        var handler = new AsyncApprovalHandler(30);
        boolean submitted = handler.submitDecision("nonexistent", ApprovalResult.approved("ok"));
        assertThat(submitted).isFalse();
    }

    @Test
    void getPendingApprovals_reflectsState() throws Exception {
        var handler = new AsyncApprovalHandler(30);

        assertThat(handler.getPendingApprovals()).isEmpty();

        // Start handle on background thread
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> handler.handle(SAMPLE_REQUEST));

        assertPendingWithin(handler, "task-123", 2000);

        var pending = handler.getPendingApprovals();
        assertThat(pending).containsKey("task-123");
        assertThat(pending.get("task-123").toolName()).isEqualTo("run_command");

        // Complete and verify removed
        handler.submitDecision("task-123", ApprovalResult.approved("ok"));
        future.get(5, TimeUnit.SECONDS);

        // Give a moment for cleanup
        Thread.sleep(50);
        assertThat(handler.getPendingApprovals()).isEmpty();
    }

    private void assertPendingWithin(AsyncApprovalHandler handler, String requestId, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!handler.getPendingApprovals().containsKey(requestId)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Request " + requestId + " did not appear in pending within " + timeoutMs + "ms");
            }
            Thread.sleep(10);
        }
    }
}
