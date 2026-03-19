package com.javaclaw.core.channel.slack;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.channel.MessageChannel;
import com.javaclaw.core.channel.TaskProgressListener;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.policy.PolicyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackTaskProgressListener implements TaskProgressListener {

    private static final Logger log = LoggerFactory.getLogger(SlackTaskProgressListener.class);
    private static final int MAX_OUTPUT_LENGTH = 500;

    private final MessageChannel channel;
    private final String channelId;
    private final String threadTs;

    public SlackTaskProgressListener(MessageChannel channel, String channelId, String threadTs) {
        this.channel = channel;
        this.channelId = channelId;
        this.threadTs = threadTs;
    }

    @Override
    public void onTaskStarted(AgentTask task) {
        sendThreadMessage("Agent started working on: *" + task.getGoal() + "*");
    }

    @Override
    public void onToolRequested(AgentTask task, String toolName, String input) {
        sendThreadMessage("Requesting tool: `" + toolName + "` with input: `" + truncate(input) + "`");
    }

    @Override
    public void onPolicyChecked(AgentTask task, String toolName, PolicyDecision decision) {
        sendThreadMessage("Policy check for `" + toolName + "`: " + decision);
    }

    @Override
    public void onApprovalRequested(AgentTask task, ApprovalRequest request) {
        try {
            channel.sendApprovalRequest(channelId, threadTs, request);
        } catch (Exception e) {
            log.error("Failed to send approval request to Slack: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onApprovalDecision(AgentTask task, ApprovalResult result) {
        String status = result.approved() ? "Approved" : "Rejected";
        sendThreadMessage("Approval decision: *" + status + "* — " + result.reason());
    }

    @Override
    public void onToolExecuted(AgentTask task, String toolName, String result) {
        sendThreadMessage("Tool `" + toolName + "` executed. Result: `" + truncate(result) + "`");
    }

    @Override
    public void onTaskCompleted(AgentTask task) {
        sendThreadMessage("Task completed. Result: " + truncate(task.getResult()));
    }

    @Override
    public void onTaskFailed(AgentTask task, String error) {
        sendThreadMessage("Task failed: " + truncate(error));
    }

    private void sendThreadMessage(String text) {
        try {
            channel.sendMessageInThread(channelId, threadTs, text);
        } catch (Exception e) {
            log.error("Failed to send progress message to Slack: {}", e.getMessage(), e);
        }
    }

    private static String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_OUTPUT_LENGTH) return text;
        return text.substring(0, MAX_OUTPUT_LENGTH) + "...";
    }
}
