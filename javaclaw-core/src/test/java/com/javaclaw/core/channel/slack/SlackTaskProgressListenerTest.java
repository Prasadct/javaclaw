package com.javaclaw.core.channel.slack;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.channel.MessageChannel;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.model.RiskLevel;
import com.javaclaw.core.policy.PolicyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlackTaskProgressListenerTest {

    private MessageChannel messageChannel;
    private SlackTaskProgressListener listener;
    private AgentTask task;

    private static final String CHANNEL_ID = "C123";
    private static final String THREAD_TS = "1234567890.123456";

    @BeforeEach
    void setUp() {
        messageChannel = mock(MessageChannel.class);
        listener = new SlackTaskProgressListener(messageChannel, CHANNEL_ID, THREAD_TS);
        task = new AgentTask("Find the bug");
    }

    @Test
    void onTaskStarted_sendsThreadMessage() {
        listener.onTaskStarted(task);

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("Agent started working on")));
    }

    @Test
    void onToolRequested_sendsThreadMessage() {
        listener.onToolRequested(task, "read_file", "src/Main.java");

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("read_file")));
    }

    @Test
    void onPolicyChecked_sendsThreadMessage() {
        listener.onPolicyChecked(task, "run_command", PolicyDecision.REQUIRE_APPROVAL);

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("REQUIRE_APPROVAL")));
    }

    @Test
    void onApprovalRequested_delegatesToSendApprovalRequest() {
        var request = new ApprovalRequest("task-1", "Fix bug", "run_command", "ls",
                RiskLevel.HIGH, "Policy requires approval");

        listener.onApprovalRequested(task, request);

        verify(messageChannel).sendApprovalRequest(eq(CHANNEL_ID), eq(THREAD_TS), eq(request));
        verify(messageChannel, never()).sendMessageInThread(any(), any(), any());
    }

    @Test
    void onApprovalDecision_approved_sendsThreadMessage() {
        listener.onApprovalDecision(task, ApprovalResult.approved("Looks safe"));

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("Approved")));
    }

    @Test
    void onApprovalDecision_rejected_sendsThreadMessage() {
        listener.onApprovalDecision(task, ApprovalResult.rejected("Too risky"));

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("Rejected")));
    }

    @Test
    void onToolExecuted_sendsThreadMessage() {
        listener.onToolExecuted(task, "read_file", "file contents here");

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("read_file")));
    }

    @Test
    void onTaskCompleted_sendsThreadMessage() {
        task.setResult("Bug fixed successfully");
        listener.onTaskCompleted(task);

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("Task completed")));
    }

    @Test
    void onTaskFailed_sendsThreadMessage() {
        listener.onTaskFailed(task, "Something went wrong");

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("Task failed")));
    }

    @Test
    void onToolExecuted_longOutput_truncated() {
        String longResult = "x".repeat(1000);
        listener.onToolExecuted(task, "read_file", longResult);

        verify(messageChannel).sendMessageInThread(eq(CHANNEL_ID), eq(THREAD_TS),
                argThat(msg -> msg.contains("...") && msg.length() < 1000));
    }

    @Test
    void onTaskStarted_channelException_doesNotThrow() {
        doThrow(new RuntimeException("Slack error")).when(messageChannel)
                .sendMessageInThread(any(), any(), any());

        // Should not throw
        listener.onTaskStarted(task);
    }
}
