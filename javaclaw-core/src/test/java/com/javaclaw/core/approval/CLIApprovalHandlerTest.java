package com.javaclaw.core.approval;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CLIApprovalHandlerTest {

    private static final ApprovalRequest SAMPLE_REQUEST = new ApprovalRequest(
            "task-123", "Test goal", "run_command", "ls -la",
            RiskLevel.HIGH, "Requires human approval"
    );

    private CLIApprovalHandler createHandler(String input, int timeoutSeconds) {
        var inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        var outputStream = new PrintStream(new ByteArrayOutputStream());
        return new CLIApprovalHandler(inputStream, outputStream, timeoutSeconds);
    }

    @Test
    void approve_returnsApproved() {
        var handler = createHandler("approve\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isTrue();
        assertThat(result.decidedAt()).isNotNull();
    }

    @Test
    void reject_returnsRejected() {
        var handler = createHandler("reject\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isFalse();
        assertThat(result.decidedAt()).isNotNull();
    }

    @Test
    void shorthandYes_returnsApproved() {
        var handler = createHandler("y\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void shorthandNo_returnsRejected() {
        var handler = createHandler("n\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isFalse();
    }

    @Test
    void yesFullWord_returnsApproved() {
        var handler = createHandler("yes\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void noFullWord_returnsRejected() {
        var handler = createHandler("no\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isFalse();
    }

    @Test
    void invalidThenValid_rePromptsAndApproves() {
        var handler = createHandler("maybe\napprove\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void caseInsensitive_approves() {
        var handler = createHandler("APPROVE\n", 30);
        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void timeout_autoRejects() {
        // Empty input stream with 1-second timeout
        var inputStream = new ByteArrayInputStream(new byte[0]);
        var outputStream = new PrintStream(new ByteArrayOutputStream());
        var handler = new CLIApprovalHandler(inputStream, outputStream, 1);

        ApprovalResult result = handler.handle(SAMPLE_REQUEST);

        assertThat(result.approved()).isFalse();
    }
}
