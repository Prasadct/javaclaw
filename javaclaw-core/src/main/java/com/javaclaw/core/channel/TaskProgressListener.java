package com.javaclaw.core.channel;

import com.javaclaw.core.approval.ApprovalRequest;
import com.javaclaw.core.approval.ApprovalResult;
import com.javaclaw.core.model.AgentTask;
import com.javaclaw.core.policy.PolicyDecision;

/**
 * Callback interface for observing agent task execution progress.
 * Implementations can relay progress to chat platforms, logging systems, etc.
 */
public interface TaskProgressListener {

    void onTaskStarted(AgentTask task);

    void onToolRequested(AgentTask task, String toolName, String input);

    void onPolicyChecked(AgentTask task, String toolName, PolicyDecision decision);

    void onApprovalRequested(AgentTask task, ApprovalRequest request);

    void onApprovalDecision(AgentTask task, ApprovalResult result);

    void onToolExecuted(AgentTask task, String toolName, String result);

    void onTaskCompleted(AgentTask task);

    void onTaskFailed(AgentTask task, String error);
}
